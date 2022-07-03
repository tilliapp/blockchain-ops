//package app.tilli.blockchain.service
//
//import app.tilli.blockchain.codec.BlockchainClasses._
//import app.tilli.blockchain.codec.BlockchainCodec._
//import app.tilli.blockchain.codec.BlockchainConfig._
//import app.tilli.logging.Logging
//import app.tilli.utils.{InputTopic, OutputTopic}
//import fs2.kafka._
//import io.circe.optics.JsonPath.root
//import io.circe.syntax.EncoderOps
//
//import java.time.Instant
//import java.util.UUID
//
//trait StreamTrait extends Logging {
//
//  def attemptCount(tilliJsonEvent: TilliJsonEvent): Option[Int] = root.attempt.int.getOption(tilliJsonEvent.data)
//
//  def moveToError(count: Int): Boolean = count >= 10
//
//  def handleDataProviderError[F[_]](
//    committable: CommittableConsumerRecord[F, String, TilliJsonEvent],
//    throwable: Throwable,
//    inputTopic: InputTopic,
//    outputTopicFailure: OutputTopic,
//    dataProvider: DataProviderTrait,
//  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
//
//    val attempts = attemptCount(committable.record.value)
//    val tooManyAttempts = attempts.exists(moveToError)
//
//    throwable match {
//      case ex: TilliHttpCallException =>
//        if (tooManyAttempts) {
//          log.error(s"Call aborted after ${attempts} attempts (moving to error queue: ${outputTopicFailure.name}. Query: ${ex.query}")
//          toErrorProducerRecords(
//            record = committable.record,
//            offset = committable.offset,
//            request = ex.query,
//            error = Option(DataError.tooManyAttempts.toString),
//            outputTopic = outputTopicFailure,
//            dataProvider = dataProvider,
//          )
//        } else {
//          Option(ex.getCause) match {
//
//            case Some(timeoutException: java.util.concurrent.TimeoutException) =>
//              log.warn(s"Request timed out with message ${timeoutException.getMessage}. Sending event ${committable.record.value.header.eventId} back into the input queue ${inputTopic.name}")
//              toRetryPageProducerRecords(committable.record, committable.offset, inputTopic)
//
//            case Some(httpClientError: HttpClientError) =>
//              httpClientError.code match {
//                case x if x.contains(429) || x.exists(_ >= 500) =>
//                  log.warn(s"Request got throttled by data provider. Sending eventId=${committable.record.value.header.eventId} back into the input queue ${inputTopic.name}. Full error: ${httpClientError.asJson.noSpaces} ")
//                  toRetryPageProducerRecords(committable.record, committable.offset, inputTopic)
//                case errCode =>
//                  log.error(s"Unrecoverable error ($errCode). Sending eventId=${committable.record.value.header.eventId} to error queue ${outputTopicFailure.name}")
//                  toErrorProducerRecords(
//                    record = committable.record,
//                    offset = committable.offset,
//                    request = httpClientError.url,
//                    error = Option(throwable.getMessage).orElse(Option(DataError.httpClientError.toString)),
//                    outputTopic = outputTopicFailure,
//                    dataProvider = dataProvider,
//                  )
//              }
//            case Some(throwable: Throwable) => unknownError(throwable, committable, outputTopicFailure, dataProvider)
//            case None => unknownError(throwable, committable, outputTopicFailure, dataProvider)
//
//          }
//        }
//
//      case throwable: Throwable => unknownError(throwable, committable, outputTopicFailure, dataProvider)
//    }
//  }
//
//  def unknownError[F[_]](
//    throwable: Throwable,
//    committable: CommittableConsumerRecord[F, String, TilliJsonEvent],
//    outputTopicFailure: OutputTopic,
//    dataProvider: DataProviderTrait,
//  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
//    log.error(s"Unknown Error occurred: ${throwable.getMessage}: $throwable")
//    toErrorProducerRecords(
//      record = committable.record,
//      offset = committable.offset,
//      request = None,
//      error = Option(throwable.getMessage),
//      outputTopic = outputTopicFailure,
//      dataProvider = dataProvider,
//    )
//  }
//
//  def toRetryPageProducerRecords[F[_]](
//    record: ConsumerRecord[String, TilliJsonEvent],
//    offset: CommittableOffset[F],
//    inputTopic: InputTopic,
//  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent]
//
//  def toErrorProducerRecords[F[_]](
//    record: ConsumerRecord[String, TilliJsonEvent],
//    offset: CommittableOffset[F],
//    request: Option[String],
//    error: Option[String],
//    outputTopic: OutputTopic,
//    dataProvider: DataProviderTrait,
//  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
//    val header = record.value.header.copy(
//      eventTimestamp = Instant.now(),
//      eventId = UUID.randomUUID(),
//      origin = record.value.header.origin ++ List(
//        Origin(
//          source = Some(dataProvider.source),
//          provider = Some(dataProvider.provider),
//          sourcedTimestamp = Instant.now,
//        )
//      ),
//      dataType = Some(DataTypeDataProviderError),
//      version = DataTypeToVersion.get(DataTypeDataProviderError)
//    )
//
//    val dataProviderError = TilliDataProviderError(
//      originalEvent = Option(record.value),
//      request = request,
//      error = error,
//      dataSource = Some(dataProvider.source),
//      dataProvider = Some(dataProvider.provider),
//    )
//    val tilliErrorJsonEvent = TilliJsonEvent(
//      header = header,
//      data = dataProviderError.asJson,
//    )
//    ProducerRecords(
//      List(ProducerRecord(outputTopic.name, record.key, tilliErrorJsonEvent)),
//      offset
//    )
//  }
//}
