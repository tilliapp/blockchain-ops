package app.tilli.blockchain.service.blockchainreader

import app.tilli.blockchain.codec.BlockchainClasses
import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.codec.BlockchainCodec._
import app.tilli.blockchain.codec.BlockchainConfig.{DataTypeAssetContractEvent, DataTypeAssetContractEventRequest, DataTypeToVersion}
import app.tilli.persistence.kafka.{KafkaConsumer, KafkaProducer}
import app.tilli.utils.{InputTopic, Logging, OutputTopic}
import cats.effect.{Async, Sync}
import fs2.kafka._
import io.circe.Json
import io.circe.optics.JsonPath.root
import io.circe.syntax.EncoderOps
import upperbound.Limiter

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.DurationLong

object AssetContractEventReader extends Logging {

  def assetContractEventRequestStream[F[_] : Async](
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
    val inputTopic = r.appConfig.inputTopicAssetContractEventRequest
    val outputTopic = r.appConfig.outputTopicAssetContractEvent

    import fs2.kafka._
    val stream =
      kafkaProducer.producerStream.flatMap(producer =>
        kafkaConsumer
          .consumerStream
          .subscribeTo(inputTopic.name)
          .records
          .mapAsync(8) { committable =>
            val trackingId = committable.record.value.header.trackingId
            processRecord(r.assetContractEventSource, committable.record, r.openSeaRateLimiter).asInstanceOf[F[Either[HttpClientErrorTrait, AssetContractEventsResult]]]
              .map {
                case Right(eventsResult) => toProducerRecords(committable.record, committable.offset, eventsResult, outputTopic, inputTopic, trackingId, r.assetContractSource)
                case Left(errorTrait) =>
                  // TODO: If error code is 429 then send it back into the queue
                  log.error(s"Call failed: ${errorTrait.message} (code ${errorTrait.code}): ${errorTrait.headers}")
                  errorTrait.code match {
                    case Some("429") =>
                      log.error(s"Request got throttled by data provider. Sending event ${committable.record.value.header.eventId} back into the input queue ${inputTopic.name}")
                      toRetryPageProducerRecords(committable.record, committable.offset, inputTopic)
                    case _ => toErrorProducerRecords(committable.offset, Json.Null, outputTopic, trackingId, r.assetContractSource)
                  }
              }
          }
          .through(fs2.kafka.KafkaProducer.pipe(kafkaProducer.producerSettings, producer))
          .map(_.passthrough)
          .through(commitBatchWithin(kafkaConsumerConfig.batchSize, kafkaConsumerConfig.batchDurationMs.milliseconds))
      )
    stream.compile.drain
  }

  def processRecord[F[_] : Sync : Async](
    source: AssetContractEventSource[F],
    record: ConsumerRecord[String, TilliJsonEvent],
    rateLimiter: Limiter[F],
  ): F[Either[HttpClientErrorTrait, AssetContractEventsResult]] = {
    source.getAssetContractAddress(record.value) match {
      case Right(assetContractAddress) =>
        //        Sync[F].delay(println(s"Processing record: $record")) *>
        source.getAssetContractEvents(
          record.value.header.trackingId,
          assetContractAddress,
          root.nextPage.string.getOption(record.value.data),
          rateLimiter
        ) //.flatTap(e   => Sync[F].delay(println(e)))
      case Left(err) => Sync[F].pure(Left(HttpClientError(err)))
    }
  }

  def toProducerRecords[F[_]](
    record: ConsumerRecord[String, TilliJsonEvent],
    offset: CommittableOffset[F],
    assetContractEvents: AssetContractEventsResult,
    outputTopic: OutputTopic,
    inputTopic: InputTopic,
    trackingId: UUID,
    dataProvider: DataProvider,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    val sourcedTime = Instant.now.toEpochMilli
    val producerRecords = assetContractEvents.events.map { eventJson =>
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
          dataType = Some(DataTypeAssetContractEvent),
          version = DataTypeToVersion.get(DataTypeAssetContractEvent)
        ),
        data = eventJson,
      )

      import cats.implicits._
      val transactionHash = root.transactionHash.string.getOption(eventJson)
      val toAddress = root.toAddress.string.getOption(eventJson)
      val key = (transactionHash <+> toAddress).getOrElse(UUID.randomUUID().toString)
      ProducerRecord(outputTopic.name, key, tilliJsonEvent)
    }

    // TODO: Ideally make this specific to the dataprovider, but for now it will handle it fine if openseaslug is missing.
    val nextRecord = assetContractEvents.nextPage.map { np =>
      val assetContractAddress = root.assetContractAddress.string.getOption(record.value.data)
      val addressRequest = AssetContractHolderRequest(
        assetContractAddress = assetContractAddress,
        openSeaCollectionSlug = root.openSeaCollectionSlug.string.getOption(record.value.data),
        nextPage = Option(np).filter(s => s != null && s.nonEmpty),
      )
      val tilliJsonEvent = TilliJsonEvent(
        BlockchainClasses.Header(
          trackingId = trackingId,
          eventTimestamp = Instant.now().toEpochMilli,
          eventId = UUID.randomUUID(),
          origin = List(
            Origin(
              source = Some(dataProvider.source),
              provider = Some(dataProvider.provider),
              sourcedTimestamp = sourcedTime,
            )
          ),
          dataType = Some(DataTypeAssetContractEventRequest),
          version = DataTypeToVersion.get(DataTypeAssetContractEventRequest)
        ),
        data = addressRequest.asJson,
      )
      val key = assetContractAddress.getOrElse(UUID.randomUUID().toString)
      ProducerRecord(inputTopic.name, key, tilliJsonEvent)
    }

    ProducerRecords(
      producerRecords ++ nextRecord,
      offset
    )
  }

  def toRetryPageProducerRecords[F[_]](
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
