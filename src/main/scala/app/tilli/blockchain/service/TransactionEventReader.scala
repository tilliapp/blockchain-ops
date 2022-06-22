package app.tilli.blockchain.service

import app.tilli.api.utils.HttpClientErrorTrait
import app.tilli.blockchain.codec.BlockchainClasses
import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.codec.BlockchainCodec._
import app.tilli.blockchain.codec.BlockchainConfig.{DataTypeAssetContractEvent, DataTypeAssetContractEventRequest, DataTypeToVersion, DataTypeTransactionEvent}
import app.tilli.persistence.kafka.{KafkaConsumer, KafkaProducer}
import app.tilli.utils.{InputTopic, Logging, OutputTopic}
import cats.data.EitherT
import cats.effect.{Async, Sync}
import fs2.kafka._
import io.circe.Json
import io.circe.optics.JsonPath.root
import io.circe.syntax.EncoderOps
import upperbound.Limiter

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.DurationLong

object TransactionEventReader extends Logging {

  def transactionEventRequestStream[F[_] : Async](
    r: Resources,
  )(implicit
    valueDeserializer: Deserializer[F, TilliJsonEvent],
    keySerializer: RecordSerializer[F, String],
    valueSerializer: RecordSerializer[F, TilliJsonEvent]
  ): F[Unit] = {
    val kafkaConsumerConfig = r.appConfig.kafkaConsumerConfiguration
    val kafkaProducerConfig = r.appConfig.kafkaProducerConfiguration
    val kafkaConsumer = new KafkaConsumer[String, TilliJsonEvent](kafkaConsumerConfig)
    val kafkaProducer = new KafkaProducer[String, TilliJsonEvent](kafkaProducerConfig)
    val inputTopic = r.appConfig.inputTopicTransactionEventRequest
    val outputTopic = r.appConfig.outputTopicTransactionEvent

    import cats.implicits._
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
            processRecord(r.transactionEventSource, committable.record, r.covalentHqRateLimiter).asInstanceOf[F[Either[Throwable, List[TransactionEventsResult]]]]
              .map {
                case Right(eventsResult) =>
                  toProducerRecords(committable.record, committable.offset, eventsResult, outputTopic, inputTopic, trackingId, r.transactionEventSource)
                case Left(errorTrait) =>
                  errorTrait match {
                    case httpClientErrorTrait: HttpClientErrorTrait =>
                      // TODO: If error code is 429 then send it back into the queue
                      log.error(s"Call failed: ${httpClientErrorTrait.message} (code ${httpClientErrorTrait.code}): ${httpClientErrorTrait.headers}")
                      httpClientErrorTrait.code match {
                        case Some("429") =>
                          log.error(s"Request got throttled by data provider. Sending event ${committable.record.value.header.eventId} back into the input queue ${inputTopic.name}")
                          toRetryPageProducerRecords(committable.record, committable.offset, inputTopic)
                        case _ => toErrorProducerRecords(committable.offset, Json.Null, outputTopic, trackingId, r.transactionEventSource)
                      }
                    case throwable: Throwable =>
                      log.error(s"Unknown Error: ${throwable.getMessage}: ${throwable}")
                      toErrorProducerRecords(committable.offset, Json.Null, outputTopic, trackingId, r.transactionEventSource)
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
    source: TransactionEventSource[F],
    record: ConsumerRecord[String, TilliJsonEvent],
    rateLimiter: Limiter[F],
  ): F[Either[Throwable, List[TransactionEventsResult]]] = {
    val toAddress = root.toAddress.string.getOption(record.value.data).toRight(new IllegalStateException(s"No toAddress was found for record tracking id ${record.value.header.trackingId}")).asInstanceOf[Either[Throwable, String]]
    val fromAddress = root.fromAddress.string.getOption(record.value.data).toRight(new IllegalStateException(s"No fromAddress was found for record tracking id ${record.value.header.trackingId}")).asInstanceOf[Either[Throwable, String]]
    val blockChain = root.chain.string.getOption(record.value.data).toRight(new IllegalStateException(s"No chain information was found for record tracking id ${record.value.header.trackingId}")).asInstanceOf[Either[Throwable, String]]
    val nextPage = root.nextPage.int.getOption(record.value.data)

    val chain = for {
      bc <- EitherT(Sync[F].pure(blockChain))
      ta <- EitherT(Sync[F].pure(toAddress))
      fa <- EitherT(Sync[F].pure(fromAddress))
      toAddressResult <- EitherT(source.getTransactionEvents(ta, bc, rateLimiter).asInstanceOf[F[Either[Throwable, TransactionEventsResult]]])
      fromAddressResult <- EitherT(source.getTransactionEvents(fa, bc, rateLimiter).asInstanceOf[F[Either[Throwable, TransactionEventsResult]]])
    } yield List(toAddressResult, fromAddressResult)

    chain.value
  }

  def toProducerRecords[F[_]](
    record: ConsumerRecord[String, TilliJsonEvent],
    offset: CommittableOffset[F],
    transactionEventsResults: List[TransactionEventsResult],
    outputTopic: OutputTopic,
    inputTopic: InputTopic,
    trackingId: UUID,
    dataProvider: DataProvider,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    val sourcedTime = Instant.now.toEpochMilli
    ???
//    transactionEventsResults.map { ter =>
//      val eventRecord = ter.events.map { eventJson =>
//        val tilliJsonEvent = TilliJsonEvent(
//          BlockchainClasses.Header(
//            trackingId = trackingId,
//            eventTimestamp = sourcedTime,
//            eventId = UUID.randomUUID(),
//            origin = List(
//              Origin(
//                source = Some(dataProvider.source),
//                provider = Some(dataProvider.provider),
//                sourcedTimestamp = sourcedTime,
//              )
//            ),
//            dataType = Some(DataTypeTransactionEvent),
//            version = DataTypeToVersion.get(DataTypeTransactionEvent)
//          ),
//          data = eventJson,
//        )
//
//        import cats.implicits._
//        val transactionHash = root.transactionHash.string.getOption(eventJson)
//        val key = transactionHash.getOrElse(eventJson.toString.hashCode.toString) // TODO: Align on the hashcode
//        ProducerRecord(outputTopic.name, key, tilliJsonEvent)
//      }
//
//      // TODO: Ideally make this specific to the dataprovider, but for now it will handle it fine if openseaslug is missing.
//      val nextRecord = ter.nextPage.map { np =>
//        val assetContractAddress = root.assetContractAddress.string.getOption(record.value.data)
//        val addressRequest = AssetContractHolderRequest(
//          assetContractAddress = assetContractAddress,
//          openSeaCollectionSlug = root.openSeaCollectionSlug.string.getOption(record.value.data),
//          nextPage = Option(np)
//        )
//        val tilliJsonEvent = TilliJsonEvent(
//          BlockchainClasses.Header(
//            trackingId = trackingId,
//            eventTimestamp = Instant.now().toEpochMilli,
//            eventId = UUID.randomUUID(),
//            origin = List(
//              Origin(
//                source = Some(dataProvider.source),
//                provider = Some(dataProvider.provider),
//                sourcedTimestamp = sourcedTime,
//              )
//            ),
//            dataType = Some(DataTypeAssetContractEvent),
//            version = DataTypeToVersion.get(DataTypeAssetContractEvent)
//          ),
//          data = addressRequest.asJson,
//        )
//        val key = assetContractAddress.getOrElse(UUID.randomUUID().toString)
//        ProducerRecord(inputTopic.name, key, tilliJsonEvent)
//      }
//    }
//
//    ProducerRecords(
//      producerRecords ++ nextRecord,
//      offset
//    )
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
