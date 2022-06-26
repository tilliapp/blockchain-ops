package app.tilli.blockchain.service

import app.tilli.blockchain.codec.BlockchainClasses
import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.codec.BlockchainCodec._
import app.tilli.blockchain.codec.BlockchainConfig.{DataTypeAssetContractEvent, DataTypeToVersion, DataTypeTransactionEvent}
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
    val kafkaConsumer = new KafkaConsumer[String, TilliJsonEvent](kafkaConsumerConfig, r.sslConfig)
    val kafkaProducer = new KafkaProducer[String, TilliJsonEvent](kafkaProducerConfig, r.sslConfig)
    val inputTopic = r.appConfig.inputTopicTransactionEventRequest
    val outputTopic = r.appConfig.outputTopicTransactionEvent
    val outputTopicFailure = r.appConfig.outputTopicFailureEvent

    import cats.implicits._
    import fs2.kafka._
    val stream =
      kafkaProducer.producerStream.flatMap(producer =>
        kafkaConsumer
          .consumerStream
          .subscribeTo(inputTopic.name)
          .partitionedRecords
          .map { partition =>
            partition.evalMap { committable =>
              val trackingId = committable.record.value.header.trackingId
              processRecord(r.transactionEventSource, committable.record, r.covalentHqRateLimiter).asInstanceOf[F[Either[Throwable, TransactionEventsResult]]]
                .map {
                  case Right(eventsResult) =>
                    toProducerRecords(committable.record, committable.offset, eventsResult, outputTopic, inputTopic, trackingId, r.transactionEventSource)
                  case Left(errorTrait) =>
                    errorTrait match {
                      case httpClientError: HttpClientError =>
                        // TODO: If error code is 429 then send it back into the queue
                        log.error(s"Call failed: ${httpClientError.message} (code ${httpClientError.code}): ${httpClientError.headers}")
                        httpClientError.code match {
                          case Some("429") =>
                            log.error(s"Request got throttled by data provider. Sending event ${committable.record.value.header.eventId} back into the input queue ${inputTopic.name}")
                            toRetryPageProducerRecords(committable.record, committable.offset, inputTopic)
                          case _ => toErrorProducerRecords(committable.record, committable.offset, httpClientError.asJson, outputTopicFailure, trackingId, r.transactionEventSource)
                        }
                      case throwable: Throwable =>
                        log.error(s"Unknown Error: ${throwable.getMessage}: ${throwable}")
                        val error = HttpClientError(throwable)
                        toErrorProducerRecords(committable.record, committable.offset, error.asJson, outputTopicFailure, trackingId, r.transactionEventSource)
                    }
                }
                .flatTap(r => Sync[F].delay(log.info(s"Wrote ${r.records.size} for ${committable.offset}")))
            }
          }.parJoinUnbounded
          .through(fs2.kafka.KafkaProducer.pipe(kafkaProducer.producerSettings, producer))
          .map(_.passthrough)
          .through(commitBatchWithin(kafkaConsumerConfig.batchSize, kafkaConsumerConfig.batchDurationMs.milliseconds))
      )
    stream.compile.drain
  }

  def processRecord[F[_] : Sync : Async](
    source: TransactionEventSource[F],
    record: ConsumerRecord[String, TilliJsonEvent],
    rateLimiter: Limiter[F],
  ): F[Either[Throwable, TransactionEventsResult]] = {

    val address = root.address.string.getOption(record.value.data).toRight(new IllegalStateException(s"No toAddress was found for record tracking id ${record.value.header.trackingId}")).asInstanceOf[Either[Throwable, String]]
    val blockChain = root.chain.string.getOption(record.value.data).toRight(new IllegalStateException(s"No chain information was found for record tracking id ${record.value.header.trackingId}")).asInstanceOf[Either[Throwable, String]]
    val nextPage = root.nextPage.string.getOption(record.value.data)

    val chain = for {
      bc <- EitherT(Sync[F].pure(blockChain))
      address <- EitherT(Sync[F].pure(address))
      toAddressResult <- EitherT(source.getTransactionEvents(address, bc, nextPage, rateLimiter).asInstanceOf[F[Either[Throwable, TransactionEventsResult]]])
    } yield toAddressResult

    chain.value
  }

  def toProducerRecords[F[_]](
    record: ConsumerRecord[String, TilliJsonEvent],
    offset: CommittableOffset[F],
    transactionEventsResults: TransactionEventsResult,
    outputTopic: OutputTopic,
    inputTopic: InputTopic,
    trackingId: UUID,
    dataProvider: DataProvider,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    val sourcedTime = Instant.now.toEpochMilli

    val transactionalProducerRecords = transactionEventsResults.events
      .filterNot{d =>
        val stop = d.isNull
        stop
      }
      .map(eventJson => {
      val tilliJsonEvent = TilliJsonEvent(
        BlockchainClasses.Header(
          trackingId = trackingId,
          eventTimestamp = sourcedTime,
          eventId = UUID.randomUUID(),
          origin = record.value.header.origin ++ List(
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
      ProducerRecord(outputTopic.name, key, tilliJsonEvent)
    })

    val nextPageProducerRecord = for {
      np <- transactionEventsResults.nextPage
      address <- root.address.string.getOption(record.value.data)
      chain = root.chain.string.getOption(record.value.data)
    } yield {
      val req = AddressRequest(
        address = address,
        chain = chain,
        nextPage = Some(np.toString),
      )
      val tilliJsonEvent = TilliJsonEvent(
        BlockchainClasses.Header(
          trackingId = trackingId,
          eventTimestamp = Instant.now().toEpochMilli,
          eventId = UUID.randomUUID(),
          origin = record.value.header.origin ++ List(
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

    ProducerRecords(
      transactionalProducerRecords ++ nextPageProducerRecord,
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
    record: ConsumerRecord[String, TilliJsonEvent],
    offset: CommittableOffset[F],
    error: Json,
    outputTopic: OutputTopic,
    trackingId: UUID,
    dataProvider: DataProvider,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    val errorEvent = record.value.copy(
      header = record.value.header.copy(
        eventTimestamp = Instant.now().toEpochMilli,
        eventId = UUID.randomUUID(),
        origin = record.value.header.origin ++ List(
          Origin(
            source = Some(dataProvider.source),
            provider = Some(dataProvider.provider),
            sourcedTimestamp = Instant.now.toEpochMilli,
          )
        ),
      ),
      data = error,
//      originalEvent = record.value.asJson,
    )
    ProducerRecords(
      List(ProducerRecord(outputTopic.name, record.key, errorEvent)),
      offset
    )
  }

}
