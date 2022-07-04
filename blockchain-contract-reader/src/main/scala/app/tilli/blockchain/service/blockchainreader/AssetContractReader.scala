package app.tilli.blockchain.service.blockchainreader

import app.tilli.blockchain.codec.BlockchainClasses
import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.codec.BlockchainCodec._
import app.tilli.blockchain.codec.BlockchainConfig.{DataTypeAssetContract, DataTypeAssetContractEventRequest, DataTypeToVersion}
import app.tilli.blockchain.service.StreamTrait
import app.tilli.persistence.kafka.{KafkaConsumer, KafkaProducer}
import app.tilli.utils.{DateUtils, InputTopic, OutputTopic}
import cats.effect.{Async, Sync}
import fs2.kafka._
import io.circe.Json
import io.circe.optics.JsonPath.root
import io.circe.syntax.EncoderOps
import upperbound.Limiter

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.DurationLong

object AssetContractReader extends StreamTrait {

  def assetContractRequestsStream[F[_] : Async](
    r: Resources,
  )(implicit
    valueDeserializer: Deserializer[F, TilliJsonEvent],
    keySerializer: RecordSerializer[F, String],
    valueSerializer: RecordSerializer[F, TilliJsonEvent]
  ): F[Unit] = {
    import cats.implicits._
    val kafkaConsumerConfig = r.appConfig.kafkaConsumerConfiguration
    val kafkaProducerConfig = r.appConfig.kafkaProducerConfiguration
    val kafkaConsumer = new KafkaConsumer[String, TilliJsonEvent](kafkaConsumerConfig, r.sslConfig)
    val kafkaProducer = new KafkaProducer[String, TilliJsonEvent](kafkaProducerConfig, r.sslConfig)
    val inputTopic = r.appConfig.inputTopicAssetContractRequest
    val outputTopicAssetContract = r.appConfig.outputTopicAssetContract
    val outputTopicAssetContractRequest = r.appConfig.outputTopicAssetContractEventRequest
    val outputTopicFailure = r.appConfig.outputTopicFailureEvent

    import fs2.kafka._
    val stream =
      kafkaProducer
        .producerStream
        .flatMap(producer =>
          kafkaConsumer
            .consumerStream
            .subscribeTo(inputTopic.name)
            .records
            .mapAsync(2) { committable =>
              val record = processRecord(r.assetContractSource, committable.record, r.openSeaRateLimiter).asInstanceOf[F[Either[HttpClientErrorTrait, Json]]]
              record.map {
                case Right(result) => toProducerRecords(committable, result, outputTopicAssetContract, outputTopicAssetContractRequest, r.assetContractSource)
                case Left(errorTrait) => handleDataProviderError(committable, errorTrait, inputTopic, outputTopicFailure, r.assetContractSource)
              }.flatTap(r => Sync[F].delay(log.info(s"eventId=${committable.record.value.header.eventId}: Emitted=${r.records.size}. Committed=${committable.offset.topicPartition}:${committable.offset.offsetAndMetadata.offset}")))
            }
            .through(fs2.kafka.KafkaProducer.pipe(kafkaProducer.producerSettings, producer))
            .map(_.passthrough)
            .through(commitBatchWithin(kafkaConsumerConfig.batchSize, kafkaConsumerConfig.batchDurationMs.milliseconds))
        )
    stream.compile.drain
  }

  def processRecord[F[_] : Sync : Async](
    source: AssetContractSource[F],
    record: ConsumerRecord[String, TilliJsonEvent],
    rateLimiter: Limiter[F],
  ): F[Either[Throwable, Json]] = {
    import cats.implicits._
    Sync[F].delay(log.info(s"Starting initial sync for asset contract: ${record.value.asJson.spaces2}")) *>
      source.getAssetContract(
        root.assetContractAddress.string.getOption(record.value.data).get,
        Some(rateLimiter),
      )
  }

  def toProducerRecords[F[_]](
    record: CommittableConsumerRecord[F, String, TilliJsonEvent],
    result: Json,
    assetContractTopic: OutputTopic,
    assetContractEventRequestTopic: OutputTopic,
    dataProvider: DataProviderTrait,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    val trackingId = record.record.value.header.trackingId
    val key = root.address.string.getOption(result).orNull
    val sourced = DateUtils.tsToInstant(root.sourced.string.getOption(result)).getOrElse(Instant.now())
    val tilliJsonEvent1 = TilliJsonEvent(
      BlockchainClasses.Header(
        trackingId = trackingId,
        eventTimestamp = Instant.now(),
        eventId = UUID.randomUUID(),
        origin = record.record.value.header.origin ++ List(
          Origin(
            source = Some(dataProvider.source),
            provider = Some(dataProvider.provider),
            sourcedTimestamp = sourced,
          )
        ),
        dataType = Some(DataTypeAssetContract),
        version = DataTypeToVersion.get(DataTypeAssetContract)
      ),
      data = result,
    )
    val assetContractProducerRecord = ProducerRecord(assetContractTopic.name, key, tilliJsonEvent1)

    val addressRequest = AssetContractHolderRequest(
      assetContractAddress = root.address.string.getOption(result),
      openSeaCollectionSlug = root.openSeaSlug.string.getOption(result),
      nextPage = None,
    )

    val tilliJsonEvent2 = TilliJsonEvent(
      BlockchainClasses.Header(
        trackingId = trackingId,
        eventTimestamp = Instant.now(),
        eventId = UUID.randomUUID(),
        origin = List(
          Origin(
            source = Some(dataProvider.source),
            provider = Some(dataProvider.provider),
            sourcedTimestamp = sourced,
          )
        ),
        dataType = Some(DataTypeAssetContractEventRequest),
        version = DataTypeToVersion.get(DataTypeAssetContractEventRequest)
      ),
      data = addressRequest.asJson,
    )
    val assetContractEventRequestProducerRecord = ProducerRecord(assetContractEventRequestTopic.name, key, tilliJsonEvent2)

    ProducerRecords(
      records = List(
        assetContractProducerRecord,
        assetContractEventRequestProducerRecord,
      ),
      record.offset
    )
  }

  override def toRetryPageProducerRecords[F[_]](
    record: ConsumerRecord[String, TilliJsonEvent],
    offset: CommittableOffset[F],
    inputTopic: InputTopic,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = ???

}
