package app.tilli.blockchain.service.blockchainreader

import app.tilli.blockchain.codec.BlockchainClasses
import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.codec.BlockchainCodec._
import app.tilli.blockchain.codec.BlockchainConfig._
import app.tilli.blockchain.service.StreamTrait
import app.tilli.collection.AddressRequestCache
import app.tilli.persistence.kafka.{KafkaConsumer, KafkaProducer}
import app.tilli.utils.{InputTopic, OutputTopic}
import cats.MonadThrow
import cats.data.EitherT
import cats.effect.{Async, Sync}
import fs2.kafka._
import io.chrisdavenport.mules.Cache
import io.circe.optics.JsonPath.root
import io.circe.syntax.EncoderOps
import mongo4cats.collection.MongoCollection
import mongo4cats.collection.operations.{Filter, Sort}
import upperbound.Limiter

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.DurationLong

object TransactionEventReader extends StreamTrait {

  def transactionEventRequestStream[F[_] : Async](
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
    val inputTopic = r.appConfig.inputTopicTransactionEventRequest
    val outputTopicTransaction = r.appConfig.outputTopicTransactionEvent
    val outputTopicFailure = r.appConfig.outputTopicFailureEvent
    val outputTopicCursorEvent = r.appConfig.outputTopicDataProviderCursorEvent

    import cats.implicits._
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
                filterAddressRequestCache(committable.record.value, r.addressRequestCacheTransactions, r.dataProviderCursorCollection).asInstanceOf[F[Either[Throwable, Option[AddressRequest]]]]
                  .flatMap {
                    case Right(addressRequest) =>
                      addressRequest match {
                        case Some(ar) =>
                          processRequest(r.transactionEventSource, ar, r.covalentHqRateLimiter).asInstanceOf[F[Either[Throwable, TransactionEventsResult]]]
                            .map {
                              case Right(eventsResult) => toProducerRecords(committable.record, committable.offset, eventsResult, outputTopicTransaction, outputTopicCursorEvent, inputTopic, r.transactionEventSource)
                              case Left(errorTrait) => handleDataProviderError(committable, errorTrait, inputTopic, outputTopicFailure, r.transactionEventSource)
                            }//.flatTap(r => Sync[F].delay(log.info(s"eventId=${committable.record.value.header.eventId} address=${root.address.string.getOption(committable.record.value.data)}: Emitted=${r.records.size}. Committed=${committable.offset.topicPartition}:${committable.offset.offsetAndMetadata.offset}")))

                        case None => Sync[F].pure(emptyProducerRecords(committable.offset))
                      }
                    case Left(errorTrait) => Sync[F].pure(handleDataProviderError(committable, errorTrait, inputTopic, outputTopicFailure, r.transactionEventSource))
                  }
              }
            }.parJoinUnbounded
            .through(fs2.kafka.KafkaProducer.pipe(kafkaProducer.producerSettings, producer))
            .map(_.passthrough)
            .through(commitBatchWithin(kafkaConsumerConfig.batchSize, kafkaConsumerConfig.batchDurationMs.milliseconds))
        )
    stream.compile.drain
  }

  def filterAddressRequestCache[F[_] : Sync : Async](
    tilliJsonEvent: TilliJsonEvent,
    addressRequestCache: AddressRequestCache[F],
    dataProviderCursorCollection: MongoCollection[F, DataProviderCursorRecord],
  ): F[Either[Throwable, Option[AddressRequest]]] = {
    val chain =
      for {
        addressRequest <- EitherT(Sync[F].pure(tilliJsonEvent.data.as[AddressRequest]))
        deduped <- EitherT(checkAndInsertIntoCache(addressRequest, addressRequestCache, dataProviderCursorCollection))
      } yield deduped

    chain.value
  }

  def checkAndInsertIntoCache[F[_] : Sync](
    addressRequest: AddressRequest,
    addressRequestCache: AddressRequestCache[F],
    dataProviderCursorCollection: MongoCollection[F, DataProviderCursorRecord],
    useBackend: Boolean = false,
  )(implicit
    F: MonadThrow[F],
  ): F[Either[Throwable, Option[AddressRequest]]] = {
    import cats.implicits._
    val key = AddressRequest.keyWithPage(addressRequest)
    addressRequestCache
      .lookup(addressRequest, useBackend)
//      .flatTap(r => Sync[F].delay(log.info(s"Cache for $key: $r")))
      .flatMap {
        case Left(err) => F.raiseError(err)
        case Right(res) => res match {
          case None => // cache is empty, get latest data provider cursor, if exists
            getDataProviderCursor(addressRequest, dataProviderCursorCollection)
              .flatMap {
                case Right(None) =>
                  addressRequestCache.insert(addressRequest, useBackend) *>
                    Sync[F].pure(Right(Some(addressRequest))) // No cursor was found, so run the request
                case Right(Some(_)) => Sync[F].pure(Right(None)) // Cursor was found, we already have the data, do nothing
                case Left(err) => Sync[F].pure(Left(err))
              }

          case Some(a) =>
            Sync[F].delay(log.info(s"Deduped address (cached) with key: ${key}")) *> F.pure(Right(None))
        }
      }
  }

  def getDataProviderCursor[F[_]](
    addressRequest: AddressRequest,
    dataProviderCursorCollection: MongoCollection[F, DataProviderCursorRecord],
  )(implicit
    F: MonadThrow[F],
  ): F[Either[Throwable, Option[DataProviderCursor]]] = {
    import cats.implicits._
    val dataProviderCursor = DataProviderCursor(
      dataProvider = addressRequest.dataProvider,
      address = addressRequest.address,
      cursor = addressRequest.nextPage,
      query = None,
    )
    val key = DataProviderCursor.cursorKey(dataProviderCursor)
    dataProviderCursorCollection
      .find
//      .sort(Sort.desc("createdAt"))
      .filter(Filter.eq("key", key))
      .first
      .attempt
      .map(_.map(_.map(_.data)))
  }

  def processRequest[F[_] : Sync : Async](
    source: TransactionEventSource[F],
    addressRequest: AddressRequest,
    rateLimiter: Limiter[F],
  ): F[Either[Throwable, TransactionEventsResult]] = {
    val chain = for {
      bc <- EitherT(Sync[F].pure(Right(addressRequest.chain).asInstanceOf[Either[Throwable, String]]))
      transactionResult <- EitherT(source.getTransactionEvents(addressRequest.address, bc, addressRequest.nextPage, rateLimiter).asInstanceOf[F[Either[Throwable, TransactionEventsResult]]])
    } yield transactionResult
    chain.value
  }

  def toProducerRecords[F[_]](
    record: ConsumerRecord[String, TilliJsonEvent],
    offset: CommittableOffset[F],
    transactionEventsResults: TransactionEventsResult,
    outputTopicTransaction: OutputTopic,
    outputTopicCursorEvent: OutputTopic,
    inputTopic: InputTopic,
    dataProvider: DataProviderTrait,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    val sourcedTime = Instant.now
    val trackingId = record.value.header.trackingId
    val transactionalProducerRecords = transactionEventsResults
      .events
      .filterNot(_.isNull)
      .map(eventJson => {
        val tilliJsonEvent = TilliJsonEvent(
          BlockchainClasses.Header(
            trackingId = trackingId,
            eventTimestamp = sourcedTime,
            eventId = UUID.randomUUID(),
            origin = List(
              Origin(
                source = Some(dataProvider.source),
                provider = Some(dataProvider.provider),
                sourcedTimestamp = sourcedTime,
              )
            ),
            dataType = Some(DataTypeTransactionEvent),
            version = DataTypeToVersion.get(DataTypeTransactionEvent)
          ),
          data = eventJson,
        )

        val transactionHash = root.transactionHash.string.getOption(eventJson)
        val key = transactionHash.getOrElse(eventJson.toString.hashCode.toString) // TODO: Align on the hashcode
        ProducerRecord(outputTopicTransaction.name, key, tilliJsonEvent)
      })

    val nextPageProducerRecord = for {
      np <- transactionEventsResults.nextPage
      address <- root.address.string.getOption(record.value.data)
      chain <- root.chain.string.getOption(record.value.data)
    } yield {
      val req = AddressRequest(
        address = address,
        chain = chain,
        nextPage = Some(np.toString),
        dataProvider = DataProvider(dataProvider),
      )
      val tilliJsonEvent = TilliJsonEvent(
        BlockchainClasses.Header(
          trackingId = trackingId,
          eventTimestamp = Instant.now,
          eventId = UUID.randomUUID(),
          origin = List(
            Origin(
              source = Some(dataProvider.source),
              provider = Some(dataProvider.provider),
              sourcedTimestamp = sourcedTime,
            )
          ),
          dataType = Some(DataTypeAssetContractEvent),
          version = DataTypeToVersion.get(DataTypeAssetContractEvent)
        ),
        data = req.asJson,
      )
      ProducerRecord(inputTopic.name, address, tilliJsonEvent)
    }

    val cursorRecord = for {
      cursor <- transactionEventsResults.dataProviderCursor
      address <- root.address.string.getOption(record.value.data)
    } yield {
      val tilliJsonEvent = TilliJsonEvent(
        BlockchainClasses.Header(
          trackingId = trackingId,
          eventTimestamp = Instant.now,
          eventId = UUID.randomUUID(),
          origin = List(
            Origin(
              source = Some(dataProvider.source),
              provider = Some(dataProvider.provider),
              sourcedTimestamp = sourcedTime,
            )
          ),
          dataType = Some(DataTypeDataProviderCursor),
          version = DataTypeToVersion.get(DataTypeDataProviderCursor)
        ),
        data = cursor.asJson,
      )
      ProducerRecord(outputTopicCursorEvent.name, address, tilliJsonEvent)
    }

    ProducerRecords(
      transactionalProducerRecords ++ nextPageProducerRecord ++ cursorRecord,
      offset
    )
  }

  override def toRetryPageProducerRecords[F[_]](
    record: ConsumerRecord[String, TilliJsonEvent],
    offset: CommittableOffset[F],
    inputTopic: InputTopic,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    val Right(request) = record.value.data.as[AddressRequest]
    val newAssetContractHolderRequest = request.copy(attempt = request.attempt + 1)
    val newRequestTilliJsonEvent = record.value.copy(
      header = record.value.header.copy(
        eventTimestamp = Instant.now,
        eventId = UUID.randomUUID(),
      ),
      data = newAssetContractHolderRequest.asJson
    )
    ProducerRecords(
      List(ProducerRecord(inputTopic.name, record.key, newRequestTilliJsonEvent)),
      offset
    )
  }

  def emptyProducerRecords[F[_]](
    offset: CommittableOffset[F],
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] =
    ProducerRecords(
      None,
      offset
    )

}
