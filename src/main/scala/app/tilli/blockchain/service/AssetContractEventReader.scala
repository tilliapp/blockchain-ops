package app.tilli.blockchain.service

import app.tilli.api.utils.{HttpClientError, HttpClientErrorTrait}
import app.tilli.blockchain.codec.BlockchainClasses
import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.codec.BlockchainCodec._
import app.tilli.blockchain.codec.BlockchainConfig.{DataTypeAssetContract, DataTypeAssetContractEvent, DataTypeAssetContractEventRequest, DataTypeToVersion}
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
import scala.util.Try

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
    val kafkaConsumer = new KafkaConsumer[String, TilliJsonEvent](kafkaConsumerConfig)
    val kafkaProducer = new KafkaProducer[String, TilliJsonEvent](kafkaProducerConfig)
    val inputTopic = r.appConfig.inputTopicAssetContractEventRequest
    val outputTopic = r.appConfig.outputTopicAssetContractEvent

    import fs2.kafka._
    val stream =
      kafkaProducer.producerStream.flatMap(producer =>
        kafkaConsumer
          .consumerStream
          .subscribeTo(inputTopic.name)
          .records
          //        .evalTap(r => IO(println(r.record.value)))
          .mapAsync(24) { committable =>
            val trackingId = committable.record.value.header.trackingId
            val record = processRecord(r.assetContractEventSource, committable.record, r.openSeaRateLimiter).asInstanceOf[F[Either[HttpClientErrorTrait, Json]]]
            record.map {
              case Right(json) => toProducerRecords(committable.record, committable.offset, json, outputTopic, inputTopic, trackingId, r.assetContractSource)
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
          .through(KafkaProducer.pipe(kafkaProducer.producerSettings, producer))
          .map(_.passthrough)
          .through(commitBatchWithin(kafkaConsumerConfig.batchSize, kafkaConsumerConfig.batchDurationMs.milliseconds))
      )
    stream.compile.drain
  }

  def processRecord[F[_] : Sync : Async](
    source: AssetContractEventSource[F],
    record: ConsumerRecord[String, TilliJsonEvent],
    rateLimiter: Limiter[F],
  ): F[Either[HttpClientErrorTrait, Json]] = {
    import cats.implicits._
    source.getAssetContractAddress(record.value) match {
      case Right(assetContractAddress) =>
        //        Sync[F].delay(println(s"Processing record: $record")) *>
        source.getAssetEventRequest(
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
    data: Json,
    outputTopic: OutputTopic,
    inputTopic: InputTopic,
    trackingId: UUID,
    dataProvider: DataProvider,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    val sourcedTime = Instant.now.toEpochMilli

    val producerRecords = root.assetEvents.each.json.getAll(data).map { eventJson =>
      // TODO: Needs unit test. Fails miserably if any of those fields don't exist
      val transactionHash = root.transaction.transactionHash.string.getOption(eventJson)
      val toAddress = root.toAccount.address.string.getOption(eventJson) // toAccount when transfer
      val data = Json.fromFields(
        Iterable(
          "transactionHash" -> transactionHash.map(Json.fromString),
          "eventType" -> root.eventType.string.getOption(eventJson).map(Json.fromString),
          "fromAddress" -> root.fromAccount.address.string.getOption(eventJson).map(Json.fromString), // fromAccount when transfer
          "toAddress" -> toAddress.map(Json.fromString), // toAccount when transfer
          "tokenId" -> root.asset.tokenId.string.getOption(eventJson).map(Json.fromString),
          "quantity" -> root.quantity.string.getOption(eventJson).flatMap(s => Try(s.toLong).toOption.map(Json.fromLong)),
          "transactionTime" -> root.eventTimestamp.string.getOption(eventJson)
            .map(ts => if (!ts.toLowerCase.endsWith("z")) s"${ts}Z" else ts)
            .flatMap(ts => Try(Instant.parse(ts)).toOption).map(_.toEpochMilli).map(Json.fromLong)
        ).map(t => t._1 -> t._2.getOrElse(Json.Null))
      )

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
        data = data,
      )
      val key = transactionHash.getOrElse(toAddress.getOrElse(UUID.randomUUID().toString))
      ProducerRecord(outputTopic.name, key, tilliJsonEvent)
    }

    val nextPage = root.next.string.getOption(data)
    val nextRecord = nextPage.map { np =>
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
    val newAssetContractHolderRequest = request.copy(attempt = request.attempt+1)
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
