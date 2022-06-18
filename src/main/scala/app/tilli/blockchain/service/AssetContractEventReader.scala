package app.tilli.blockchain.service

import app.tilli.api.utils.{HttpClientError, HttpClientErrorTrait}
import app.tilli.blockchain.codec.BlockchainClasses
import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.codec.BlockchainConfig.{DataTypeAssetContract, DataTypeAssetContractEvent, DataTypeAssetContractEventRequest, DataTypeToVersion}
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
    source: AssetContractEventSource[F],
    record: ConsumerRecord[String, TilliJsonEvent],
    rateLimiter: Limiter[F],
  ): F[Either[HttpClientErrorTrait, Json]] = {
    import cats.implicits._
    source.getAssetContractSlug(record.value) match {
      case Right(slug) =>
        Sync[F].delay(println(s"Processing record: $record")) *> source.getAssetEventRequest(
          record.value.header.trackingId,
          slug,
          rateLimiter
        ) //.flatTap(e => Sync[F].delay(println(e)))
      case Left(err) => Sync[F].pure(Left(HttpClientError(err)))

    }

  }

  def toProducerRecords[F[_]](
    offset: CommittableOffset[F],
    record: Json,
    outputTopic: OutputTopic,
    trackingId: UUID,
    dataProvider: DataProvider,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    val sourcedTime = Instant.now.toEpochMilli
    val nextPage = root.next.string.getOption(record)

    val producerRecords =
      root.assetEvents.each.json.getAll(record).map { assetJson =>
        // TODO: Needs unit test. Fails miserably if any of those fields don't exist
        val transactionHash = root.transaction.transactionHash.string.getOption(assetJson)
        val toAddress = root.toAccount.address.string.getOption(assetJson).map(Json.fromString) // toAccount when transfer
        val data = Json.fromFields(
          Iterable(
            "transactionHash" -> transactionHash.map(Json.fromString),
            "eventType" -> root.eventType.string.getOption(assetJson).map(Json.fromString),
            "fromAddress" -> root.fromAccount.address.string.getOption(assetJson).map(Json.fromString), // fromAccount when transfer
            "toAddress" -> toAddress, // toAccount when transfer
            "tokenId" -> root.asset.tokenId.string.getOption(assetJson).map(Json.fromString),
            "quantity" -> root.quantity.string.getOption(assetJson).flatMap(s => Try(s.toLong).toOption.map(Json.fromLong)),
            "transactionTime" -> root.eventTimestamp.string.getOption(assetJson)
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
        val key = transactionHash.getOrElse(toAddress.map(_.noSpaces).getOrElse(UUID.randomUUID().toString))
        ProducerRecord(outputTopic.name, key, tilliJsonEvent)
      }

    ProducerRecords(
      producerRecords,
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
