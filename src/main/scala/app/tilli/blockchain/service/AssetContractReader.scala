package app.tilli.blockchain.service

import app.tilli.api.utils.HttpClientErrorTrait
import app.tilli.blockchain.codec.BlockchainClasses
import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.codec.BlockchainConfig.{DataTypeAssetContract, DataTypeToVersion}
import app.tilli.persistence.kafka.{KafkaConsumer, KafkaProducer}
import app.tilli.utils.{Logging, OutputTopic}
import cats.effect.{Async, Sync}
import fs2.kafka._
import io.circe.Json
import io.circe.optics.JsonPath.root
import upperbound.Limiter

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.DurationLong
import scala.util.Try

object AssetContractReader extends Logging {

  def assetContractRequestsStream[F[_] : Async](
    r: Resources,
  )(implicit
    valueDeserializer: Deserializer[F, Json],
    keySerializer: RecordSerializer[F, String],
    valueSerializer: RecordSerializer[F, TilliJsonEvent]
  ): F[Unit] = {
    import cats.implicits._
    val kafkaConsumerConfig = r.appConfig.kafkaConsumerConfiguration
    val kafkaProducerConfig = r.appConfig.kafkaProducerConfiguration
    val kafkaConsumer = new KafkaConsumer[Option[UUID], Json](kafkaConsumerConfig)
    val kafkaProducer = new KafkaProducer[String, TilliJsonEvent](kafkaProducerConfig)
    val outputTopic = r.appConfig.outputTopicAssetContractRequest

    import fs2.kafka._
    val stream =
      kafkaProducer.producerStream.flatMap(producer =>
        kafkaConsumer
          .consumerStream
          .subscribeTo(r.appConfig.inputTopicAssetContractRequest.name)
          .records
          //        .evalTap(r => IO(println(r.record.value)))
          .mapAsync(24) { committable =>
            val trackingId = root.trackingId.string.getOption(committable.record.value).flatMap(s => Try(UUID.fromString(s)).toOption).getOrElse(UUID.randomUUID())
            val record = processRecord(r.assetContractSource, committable.record, r.openSeaRateLimiter).asInstanceOf[F[Either[HttpClientErrorTrait, Json]]]
            record.map {
              case Right(json) => toProducerRecords(committable.offset, json, outputTopic, trackingId, r.assetContractSource)
              case Left(errorTrait) =>
                log.error(s"Call failed: ${errorTrait.message} (code ${errorTrait.message}): ${errorTrait.headers}")
                toErrorProducerRecords(committable.offset, Json.Null, outputTopic, trackingId, r.assetContractSource)
            }
          }
          .through(KafkaProducer.pipe(kafkaProducer.producerSettings, producer))
          .map(_.passthrough)
          .through(commitBatchWithin(kafkaConsumerConfig.batchSize, kafkaConsumerConfig.batchDurationMs.milliseconds))
      )
    stream.compile.drain
  }

  def processRecord[F[_] : Sync : Async](
    source: AssetContractSource[F],
    record: ConsumerRecord[Option[UUID], Json],
    rateLimiter: Limiter[F],
  ): F[Either[HttpClientErrorTrait, Json]] = {
    import cats.implicits._
    Sync[F].delay(println(s"Processing record: $record")) *> source.getAssetContract(
      record.key.getOrElse(UUID.randomUUID()),
      root.assetContractAddress.string.getOption(record.value).get,
      rateLimiter
    ).flatTap(e => Sync[F].delay(println(e)))
  }

  def toProducerRecords[F[_]](
    offset: CommittableOffset[F],
    record: Json,
    outputTopic: OutputTopic,
    trackingId: UUID,
    dataProvider: DataProvider,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    val key = root.address.string.getOption(record).orNull
    val sourced = root.sourced.long.getOption(record).getOrElse(Instant.now().toEpochMilli)
    val tilliJsonEvent = TilliJsonEvent(
      BlockchainClasses.Header(
        trackingId = trackingId,
        eventTimestamp = Instant.now().toEpochMilli,
        eventId = UUID.randomUUID(),
        origin = List(
          Origin(
            source = Some(dataProvider.source),
            provider = Some(dataProvider.provider),
            sourcedTimestamp = sourced,
          )
        ),
        dataType = Some(DataTypeAssetContract),
        version = DataTypeToVersion.get(DataTypeAssetContract)
      ),
      data = record,
    )
    val producerRecord = ProducerRecord(outputTopic.name, key, tilliJsonEvent)
    ProducerRecords.one(producerRecord, offset)
  }

  def toErrorProducerRecords[F[_]](
    offset: CommittableOffset[F],
    record: Json,
    outputTopic: OutputTopic,
    trackingId: UUID,
    dataProvider: DataProvider,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    val key = root.address.string.getOption(record).orNull
    val sourced = root.sourced.long.getOption(record).getOrElse(Instant.now().toEpochMilli)
    val tilliJsonEvent = TilliJsonEvent(
      BlockchainClasses.Header(
        trackingId = trackingId,
        eventTimestamp = Instant.now().toEpochMilli,
        eventId = UUID.randomUUID(),
        origin = List(
          Origin(
            source = Some(dataProvider.source),
            provider = Some(dataProvider.provider),
            sourcedTimestamp = sourced,
          )
        ),
        dataType = Some(DataTypeAssetContract),
        version = DataTypeToVersion.get(DataTypeAssetContract)
      ),
      data = record,
    )
    val producerRecord = ProducerRecord(outputTopic.name, key, tilliJsonEvent)
    ProducerRecords(None, offset)
  }

}
