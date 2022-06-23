package app.tilli.blockchain.service

import app.tilli.blockchain.codec.BlockchainClasses
import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.codec.BlockchainCodec._
import app.tilli.blockchain.codec.BlockchainConfig.{AddressType, DataTypeAddressRequest, DataTypeToVersion}
import app.tilli.persistence.kafka.{KafkaConsumer, KafkaProducer}
import app.tilli.utils.{Logging, OutputTopic}
import cats.data.EitherT
import cats.effect.{Async, Sync}
import fs2.kafka._
import io.chrisdavenport.mules.Cache
import io.circe.Json
import io.circe.optics.JsonPath.root
import io.circe.syntax.EncoderOps
import upperbound.Limiter

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.DurationLong

object AddressFilter extends Logging {

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

    import fs2.kafka._
    val stream =
      kafkaProducer.producerStream.flatMap(producer =>
        kafkaConsumer
          .consumerStream
          .subscribeTo(inputTopic.name)
          .partitionedRecords
          .map { partition =>
            partition.evalMap { committable =>
              import cats.implicits._
              val trackingId = committable.record.value.header.trackingId
              filter(committable.record, r.addressCache, r.assetContractTypeSource, r.etherscanRateLimiter).asInstanceOf[F[Either[HttpClientErrorTrait, List[AddressRequest]]]]
                .map {
                  case Right(res) =>
                    toProducerRecords(committable.record, committable.offset, res, outputTopic, trackingId)
                  case Left(err) =>
                    log.error(s"Call failed: ${err.message} (code ${err.code}): ${err.headers.map(_.toString)}")
                    toErrorProducerRecords(committable.offset, Json.Null, outputTopic, trackingId, r.assetContractSource)
                }
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
    cache: Cache[F, String, AddressSimple],
    assetContractTypeSource: AssetContractTypeSource[F],
    rateLimiter: Limiter[F],
  ): F[Either[HttpClientErrorTrait, List[AddressRequest]]] = {
    val blockChain = root.chain.string.getOption(record.value.data)

    val addresses = List(
      root.toAddress.string.getOption(record.value.data),
      root.fromAddress.string.getOption(record.value.data),
    ).flatten

    import cats.implicits._
    val temp = addresses
      .filterNot(_ == "0x0000000000000000000000000000000000000000") // Remove null address
      .map(a =>
        EitherT(
          checkAndInsertIntoCache(a, cache, assetContractTypeSource, rateLimiter)
            .map(e => e.map(res => res.map(r => AddressRequest(r, blockChain))))
        )
      ).sequence
      .map(r => r.flatten)
      .value
    temp
  }

  def checkAndInsertIntoCache[F[_] : Sync](
    a: String,
    cache: Cache[F, String, AddressSimple],
    assetContractTypeSource: AssetContractTypeSource[F],
    rateLimiter: Limiter[F],
  ): F[Either[HttpClientErrorTrait, Option[String]]] = {
    import cats.implicits._
    cache
      .lookup(a)
      .flatMap {
        case None =>
          assetContractTypeSource.getAssetContractType(a, rateLimiter)
            .flatMap {
              case Right(addressType) =>
                val as = AddressSimple(
                  address = a,
                  isContract = addressType.map(_ == AddressType.contract),
                )
                cache.insert(a, as) *>
                  Sync[F].pure(Right(
                    if (as.isContract.contains(true)) None
                    else Option(a)
                  ))
              case Left(err) =>
                Sync[F].pure(Left(
                  err
                ).asInstanceOf[Either[HttpClientErrorTrait, Option[String]]])
            }

        case Some(_) =>
          Sync[F].pure(Right(None).asInstanceOf[Either[HttpClientErrorTrait, Option[String]]])
      }
  }

  def toProducerRecords[F[_]](
    record: ConsumerRecord[String, TilliJsonEvent],
    offset: CommittableOffset[F],
    addressRequests: List[AddressRequest],
    outputTopic: OutputTopic,
    trackingId: UUID,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
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

  def toErrorProducerRecords[F[_]](
    offset: CommittableOffset[F],
    record: Json,
    outputTopic: OutputTopic,
    trackingId: UUID,
    dataProvider: DataProvider,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    ProducerRecords(None, offset)
  }
}
