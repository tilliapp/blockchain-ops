package app.tilli.blockchain.service.blockchainreader

import app.tilli.blockchain.codec.BlockchainClasses
import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.codec.BlockchainCodec._
import app.tilli.blockchain.codec.BlockchainConfig.{AddressType, DataTypeAddressRequest, DataTypeToVersion, dataProviderCovalentHq}
import app.tilli.blockchain.service.StreamTrait
import app.tilli.collection.AddressRequestCache
import app.tilli.persistence.kafka.{KafkaConsumer, KafkaProducer}
import app.tilli.utils.{InputTopic, OutputTopic}
import cats.MonadThrow
import cats.data.EitherT
import cats.effect.{Async, Sync}
import fs2.kafka._
import io.chrisdavenport.mules.Cache
import io.circe.Json
import io.circe.optics.JsonPath.root
import io.circe.syntax.EncoderOps
import mongo4cats.collection.MongoCollection
import mongo4cats.collection.operations.{Filter, Sort}
import upperbound.Limiter

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.DurationLong

object AddressFilter extends StreamTrait {

  //  private val cache = new LRUCacheMap[String, AddressSimple](maxEntries = 20000)

  def addressFilterStream[F[_] : Async](
    r: Resources,
  )(implicit
    valueDeserializer: Deserializer[F, TilliJsonEvent],
    keySerializer: RecordSerializer[F, String],
    valueSerializer: RecordSerializer[F, TilliJsonEvent]
  ): F[Unit] = {
    val kafkaConsumerConfig = r.appConfig.kafkaConsumerConfiguration
    val kafkaProducerConfig = r.appConfig.kafkaProducerConfiguration
    val kafkaConsumer = new KafkaConsumer[String, TilliJsonEvent](kafkaConsumerConfig, r.sslConfig)
    val kafkaProducer = new KafkaProducer[String, TilliJsonEvent](kafkaProducerConfig, r.sslConfig)
    val inputTopic = r.appConfig.inputTopicAddressContractEvent
    val outputTopic = r.appConfig.outputTopicAddressRequest
    val outputTopicFailure = r.appConfig.outputTopicFailureEvent
    implicit val F = MonadThrow[F]

    import fs2.kafka._
    val stream =
      kafkaProducer
        .producerStream
        .flatMap(producer =>
          kafkaConsumer
            .consumerStream
            .subscribeTo(inputTopic.name)
            .partitionedRecords
            .map { partitionStream =>
              partitionStream.evalMap { committable =>
                import cats.implicits._
                filter(committable.record, r.addressTypeCache, r.addressRequestCache, r.dataProviderCursorCache, r.assetContractTypeSource, dataProviderCovalentHq, r.dataProviderCursorCollection, r.etherscanRateLimiter).asInstanceOf[F[Either[Throwable, List[AddressRequest]]]]
                  .map {
                    case Right(res) => toProducerRecords(committable.record, committable.offset, res, outputTopic)
                    case Left(err) => handleDataProviderError(committable, err, inputTopic, outputTopicFailure, r.transactionEventSource)
                  }.flatTap(r => Sync[F].delay(log.info(s"eventId=${committable.record.value.header.eventId}: Emitted=${r.records.size}. Committed=${committable.offset.topicPartition}:${committable.offset.offsetAndMetadata.offset}")))
              }
            }.parJoinUnbounded
            .through(fs2.kafka.KafkaProducer.pipe(kafkaProducer.producerSettings, producer))
            .map(_.passthrough)
            .through(commitBatchWithin(kafkaConsumerConfig.batchSize, kafkaConsumerConfig.batchDurationMs.milliseconds))
        )
    stream.compile.drain
  }

  def filter[F[_] : Sync : Async](
    record: ConsumerRecord[String, TilliJsonEvent],
    addressTypeCache: Cache[F, String, AddressSimple],
    addressRequestCache: AddressRequestCache[F],
    dataProviderCursorCache: Cache[F, String, DataProviderCursor],
    assetContractTypeSource: AssetContractTypeSource[F],
    dataProvider: DataProvider,
    dataProviderCursorCollection: MongoCollection[F, Json],
    rateLimiter: Limiter[F],
  ): F[Either[Throwable, List[AddressRequest]]] = {
    val blockChain = root.chain.string.getOption(record.value.data)
    val addresses = List(
      root.toAddress.string.getOption(record.value.data),
      root.fromAddress.string.getOption(record.value.data),
    ).flatten

    import cats.implicits._
    val temp = addresses
      .filterNot(_ == "0x0000000000000000000000000000000000000000") // Remove null address
      .map { a =>
        val chain = for {
          bc <- EitherT(Sync[F].pure(root.chain.string.getOption(record.value.data).toRight(new IllegalStateException(s"Missing blockchain id on event ${record.value.header.eventId}"))))
          c <- EitherT(
            checkAndInsertIntoCache(
              a,
              bc,
              addressTypeCache,
              addressRequestCache,
              dataProviderCursorCache,
              assetContractTypeSource,
              dataProvider,
              dataProviderCursorCollection,
              rateLimiter,
            )
          )
        } yield c
        chain
      }.sequence
      .map(r => r.flatten)
      .value
    temp
  }

  // Algo for address transaction pull:
  // 1. look up to check if address is a contract. If it is, then remove. This should eventually be our own cache but for now ask etherscan
  // 2. check if we just emitted a request, if we did, then remove (use 1 hour as the deduper)
  // 3. If it's not a contract and we have not just emitted a request, then submit the latest cursor.

  // TODO: Have the caches upsert to mongo on misses. Store the lookup even if no data returned and let the result age out.
  //  Only risk is that an insert into mongo wiht a cursor means that we have that data. That may not be true
  //  But we could upsert into a temporary cache so as to not rely on in memory cache only.

  def checkAndInsertIntoCache[F[_] : Sync](
    a: String,
    blockChain: String,
    addressTypeCache: Cache[F, String, AddressSimple],
    addressRequestCache: AddressRequestCache[F],
    dataProviderCursorCache: Cache[F, String, DataProviderCursor],
    assetContractTypeSource: AssetContractTypeSource[F],
    dataProvider: DataProvider,
    dataProviderCursorCollection: MongoCollection[F, Json],
    rateLimiter: Limiter[F],
  )(implicit
    F: MonadThrow[F],
  ): F[Either[Throwable, Option[AddressRequest]]] = {
    import cats.implicits._

    val addressRequestCandidate = AddressRequest(
      address = a,
      chain = blockChain,
      dataProvider = dataProvider,
      nextPage = None,
    )
    val key = AddressRequest.key(addressRequestCandidate)

    addressRequestCache
      .lookup(addressRequestCandidate)
      .flatTap(r => Sync[F].delay(log.info(s"Cache for $key: $r")))
      .flatMap {
        case Left(err) => F.raiseError(err)
        case Right(res) => res match {
          case None => // we have not emitted any requests at all
            getAddressType(addressTypeCache, assetContractTypeSource, rateLimiter, a) // Later replace with call to mongo and then etherscan if no mongo
              .flatMap {
                case Right(adt) => // if the address type is contract then skip
                  if (adt.isContract.contains(false)) {
                    checkAndInsertDataProviderCursor(adt, dataProvider, dataProviderCursorCache, dataProviderCursorCollection)
                      .map(_.map(Some(_)))
                      .map(_.map(_.map(dpc =>
                        AddressRequest(
                          dpc.address,
                          blockChain,
                          dataProvider = dataProvider,
                          nextPage = dpc.cursor,
                        )))
                      ).flatMap {
                      case Left(err) =>
                        F.raiseError(err)
                      case either@Right(result) =>
                        result.map(addressRequestCache.insert).getOrElse(F.pure(None)) *> F.pure(either)
                    }
                  } else F.pure(Right(None))
                case Left(err) => F.pure(Left(err))
              }

          case Some(_) => // We have already requested so don't request again
            Sync[F].delay(log.info(s"Deduped address (cached) with key: ${key}")) *>
              F.pure(Right(None)) // Prev requests will age out and we can then call again
        }
      }
  }

  def checkAndInsertDataProviderCursor[F[_]](
    adt: AddressSimple,
    dataProvider: DataProvider,
    dataProviderCursorCache: Cache[F, String, DataProviderCursor],
    dataProviderCursorCollection: MongoCollection[F, Json],
  )(implicit
    F: MonadThrow[F],
  ): F[Either[Throwable, DataProviderCursor]] = {
    import cats.implicits._
    val key = DataProviderCursor.key(adt.address, dataProvider)
    dataProviderCursorCache
      .lookup(key)
      .flatMap {
        case None =>
          getDataProviderCursor(adt.address, dataProviderCursorCollection, dataProvider)
            .flatMap {
              case Right(dpcOpt) =>
                val dpc = dpcOpt.map(_.data).getOrElse(
                  DataProviderCursor(
                    dataProvider = dataProvider,
                    address = adt.address,
                    cursor = None,
                    query = None,
                  )
                )
                dataProviderCursorCache.insert(key, dpc) *> F.pure(Right(dpc))
              case Left(err) => F.pure(Left(err))
            }
        case Some(cursor) => {
          F.pure(log.info(s"Starting address ${adt.address} at cursor=${cursor.cursor}")) *>
            F.pure(Right(cursor))
        }
      }
  }

  def getAddressType[F[_]](
    addressTypeCache: Cache[F, String, AddressSimple],
    assetContractTypeSource: AssetContractTypeSource[F],
    rateLimiter: Limiter[F],
    a: String
  )(implicit
    F: MonadThrow[F],
  ): F[Either[Throwable, AddressSimple]] = {
    import cats.implicits._
    addressTypeCache
      .lookup(a)
      .flatMap {
        case None =>
          assetContractTypeSource
            .getAssetContractType(a, rateLimiter)
            .flatMap {
              case Right(addressType) =>
                val as = AddressSimple(
                  address = a,
                  isContract = addressType.map(_ == AddressType.contract),
                )
                addressTypeCache.insert(a, as) *> F.pure(Right(as))
              case Left(err) => F.pure(Left(err))
            }
        case Some(adt) => F.pure(Right(adt))
      }
  }

  def getDataProviderCursor[F[_]](
    address: String,
    dataProviderCursorCollection: MongoCollection[F, Json],
    dataProvider: DataProvider,
  )(implicit
    F: MonadThrow[F],
  ): F[Either[Throwable, Option[DataProviderCursorRecord]]] = {
    import cats.implicits._
    val key = DataProviderCursor.key(address, dataProvider)
    dataProviderCursorCollection
      .find
      .sort(Sort.desc("data.data.createdAt"))
      .filter(Filter.eq("data.key", key))
      .first
      .attempt
      .map(_.map(_.flatMap(j => root.data.json.getOption(j).map(_.as[DataProviderCursorRecord]))))
      .flatMap {
        case Right(result) =>
          F.pure(
            result
              .map(_.leftMap(err => new IllegalStateException(s"Could not deserialize DataProviderCursorRecord for key $key", err)))
              .sequence
          )
        case Left(err) => F.pure(Left(err))
      }
  }

  def toProducerRecords[F[_]](
    record: ConsumerRecord[String, TilliJsonEvent],
    offset: CommittableOffset[F],
    addressRequests: List[AddressRequest],
    outputTopic: OutputTopic,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    val trackingId = record.value.header.trackingId
    val sourcedTime = Instant.now.toEpochMilli
    val requests = addressRequests.map {
      ar =>
        val tilliJsonEvent = TilliJsonEvent(
          BlockchainClasses.Header(
            trackingId = trackingId,
            eventTimestamp = sourcedTime,
            eventId = UUID.randomUUID(),
            origin = record.value.header.origin,
            dataType = Some(DataTypeAddressRequest),
            version = DataTypeToVersion.get(DataTypeAddressRequest)
          ),
          data = ar.asJson,
        )
        ProducerRecord(outputTopic.name, ar.address, tilliJsonEvent)
    }
    ProducerRecords(
      requests,
      offset
    )
  }

  override def toRetryPageProducerRecords[F[_]](
    record: ConsumerRecord[String, TilliJsonEvent],
    offset: CommittableOffset[F],
    inputTopic: InputTopic,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    val Right(request) = record.value.data.as[AssetContractHolderRequest]
    val newAssetContractHolderRequest = request.copy(attempt = request.attempt + 1)
    val newRequestTilliJsonEvent = record.value.copy(
      header = record.value.header.copy(
        eventTimestamp = Instant.now().toEpochMilli,
        eventId = UUID.randomUUID(),
      ),
      data = newAssetContractHolderRequest.asJson
    )
    ProducerRecords(
      List(ProducerRecord(inputTopic.name, record.key, newRequestTilliJsonEvent)),
      offset
    )
  }

}
