package app.tilli.blockchain.service.mongodbsink

import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.persistence.kafka.{KafkaConsumer, KafkaConsumerConfiguration}
import app.tilli.utils.{InputTopic, Logging, OutputTopic}
import cats.effect.{Async, Sync}
import fs2.Chunk
import fs2.kafka._
import io.circe.Json
import io.circe.optics.JsonPath.root

import java.time.Instant
import java.util.UUID

object MongoDbSink extends Logging {

  def streamToSink[F[_] : Async](
    r: Resources[F],
  )(implicit
    valueDeserializer: Deserializer[F, TilliJsonEvent],
  ): F[Unit] = {
    val kafkaConsumerConfig = r.appConfig.kafkaConsumerConfiguration
    val kafkaConsumer = new KafkaConsumer[String, TilliJsonEvent](kafkaConsumerConfig, r.sslConfig)
    val inputTopic = r.appConfig.inputTopicTransactionEvent
    val outputTopicFailure = r.appConfig.outputTopicFailureEvent

    stream(r, kafkaConsumer, kafkaConsumerConfig, inputTopic, outputTopicFailure)
      .compile
      .drain
  }

  def stream[F[_] : Async](
    resources: Resources[F],
    kafkaConsumer: KafkaConsumer[String, TilliJsonEvent],
    kafkaConsumerConfig: KafkaConsumerConfiguration,
    inputTopic: InputTopic,
    outputTopicFailure: OutputTopic,
  )(implicit
    valueDeserializer: Deserializer[F, TilliJsonEvent],
  ): fs2.Stream[F, Unit] = {
    import cats.implicits._
    import fs2.kafka._
    kafkaConsumer
      .consumerStream
      .subscribeTo(inputTopic.name)
      .records
      .chunks
      .evalMapChunk { chunk =>
        val batch = CommittableOffsetBatch.fromFoldableMap(chunk)(_.offset)
        val processed = write(resources, transform(chunk))
          .flatMap {
            case Right(_) =>
              Sync[F].pure()
            case Left(throwable) =>
              val error = HttpClientError(throwable)
              log.error(s"Write failed: ${error.message} (code ${error.code}): ${error.headers}")
              Sync[F].raiseError(error).asInstanceOf[F[Unit]]
          }

        Sync[F].delay(log.info(s"Writing batch of size ${chunk.size}: ${batch.offsets.lastOption.map(t => s"${t._1}:${t._2.offset()}").getOrElse("No offset")}")) *>
          processed *>
          batch.commit
      }
  }

  def transform[F[_]](
    chunkRecords: Chunk[CommittableConsumerRecord[F, String, TilliJsonEvent]],
  ): List[Json] = {
    chunkRecords
      .toList
      .map(_.record.value.data)
      .map(json =>
        Json.fromFields(
          Iterable(
            "transactionTime" -> Json.fromString(root.transactionTime.long.getOption(json).map(_.toString).get),
            "data" -> json,
          )
        )

      )
  }

  def write[F[_] : Sync : Async](
    resources: Resources[F],
    data: Seq[Json],
  ): F[Either[Throwable, Boolean]] = {
    import cats.implicits._
    resources.transactionCollection
      .insertMany(data)
      .attempt
      .map(_.map(_.wasAcknowledged()))
  }

  def toProducerRecords[F[_]](
    offset: CommittableOffset[F],
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    ProducerRecords(
      None,
      offset
    )
  }

  def toErrorProducerRecords[F[_]](
    record: ConsumerRecord[String, TilliJsonEvent],
    offset: CommittableOffset[F],
    error: Json,
    outputTopic: OutputTopic,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    val errorEvent = record.value.copy(
      header = record.value.header.copy(
        eventTimestamp = Instant.now().toEpochMilli,
        eventId = UUID.randomUUID(),
        origin = record.value.header.origin
      ),
      data = error,
    )
    ProducerRecords(
      List(ProducerRecord(outputTopic.name, record.key, errorEvent)),
      offset
    )
  }

}
