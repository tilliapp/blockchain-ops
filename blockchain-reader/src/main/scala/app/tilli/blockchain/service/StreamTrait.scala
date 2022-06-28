package app.tilli.blockchain.service


import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.codec.BlockchainCodec._
import app.tilli.blockchain.codec.BlockchainConfig._
import app.tilli.blockchain.service.blockchainreader.TransactionEventReader.log
import app.tilli.logging.Logging
import app.tilli.utils.{InputTopic, OutputTopic}
import fs2.kafka._
import io.circe.syntax.EncoderOps

import java.time.Instant
import java.util.UUID

trait StreamTrait extends Logging {

  def handleDataProviderError[F[_]](
    committable: CommittableConsumerRecord[F, String, TilliJsonEvent],
    throwable: Throwable,
    inputTopic: InputTopic,
    outputTopicFailure: OutputTopic,
    dataProvider: DataProvider,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    throwable match {
      case httpClientError: HttpClientError =>
        // TODO: If error code is 429 then send it back into the queue
        log.error(s"Call failed: ${httpClientError.message} (code ${httpClientError.code}): ${httpClientError.headers}")
        httpClientError.code match {
          case Some("429") =>
            log.error(s"Request got throttled by data provider. Sending event ${committable.record.value.header.eventId} back into the input queue ${inputTopic.name}")
            toRetryPageProducerRecords(committable.record, committable.offset, inputTopic)
          case _ =>
            // TODO: This is specific to blaze http client. Use timeout execption insteaqd
            if (httpClientError.message.contains("timed out")) {
              log.warn(s"Request timed out. Sending event ${committable.record.value.header.eventId} back into the input queue ${inputTopic.name}")
              toRetryPageProducerRecords(committable.record, committable.offset, inputTopic)
            } else
              toErrorProducerRecords(
                record = committable.record,
                offset = committable.offset,
                request = ???,
                error = Option(throwable.getMessage),
                outputTopic = outputTopicFailure,
                dataProvider = dataProvider,
              )
        }
      case throwable: Throwable =>
        log.error(s"Unknown Error: ${throwable.getMessage}: ${throwable}")
        val error = HttpClientError(throwable)
        toErrorProducerRecords(
          record = committable.record,
          offset = committable.offset,
          request = ???,
          error = Option(throwable.getMessage),
          outputTopic = outputTopicFailure,
          dataProvider = dataProvider,
        )
    }
  }

  def toRetryPageProducerRecords[F[_]](
    record: ConsumerRecord[String, TilliJsonEvent],
    offset: CommittableOffset[F],
    inputTopic: InputTopic,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent]

  def toErrorProducerRecords[F[_]](
    record: ConsumerRecord[String, TilliJsonEvent],
    offset: CommittableOffset[F],
    request: Option[String],
    error: Option[String],
    outputTopic: OutputTopic,
    dataProvider: DataProvider,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    val header = record.value.header.copy(
      eventTimestamp = Instant.now().toEpochMilli,
      eventId = UUID.randomUUID(),
      origin = record.value.header.origin ++ List(
        Origin(
          source = Some(dataProvider.source),
          provider = Some(dataProvider.provider),
          sourcedTimestamp = Instant.now.toEpochMilli,
        )
      ),
      dataType = Some(DataTypeDataProviderError),
      version = DataTypeToVersion.get(DataTypeDataProviderError)
    )

    val dataProviderError = TilliDataProviderError(
      originalEvent = Option(record.value),
      request = request,
      error = error,
      dataSource = Some(dataProvider.source),
      dataProvider = Some(dataProvider.provider),
    )
    val tilliErrorJsonEvent = TilliJsonEvent(
      header = header,
      data = dataProviderError.asJson,
    )
    ProducerRecords(
      List(ProducerRecord(outputTopic.name, record.key, tilliErrorJsonEvent)),
      offset
    )
  }
}
