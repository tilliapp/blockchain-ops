package app.tilli.blockchain.service.blockchainsink.sink

import app.tilli.blockchain.service.blockchainsink.Resources
import app.tilli.logging.Logging
import app.tilli.persistence.kafka.KafkaConsumer
import app.tilli.utils.{InputTopic, OutputTopic}
import cats.effect.{Async, Sync}
import com.mongodb.bulk.BulkWriteResult
import fs2.kafka.Deserializer

trait SinkWriter[IN] extends Logging {

  def name: String = this.getClass.getSimpleName

  def concurrency: Int = 4

  def streamIntoDatabase[F[_] : Async](
    resources: Resources[F],
    kafkaConsumer: KafkaConsumer[String, IN],
    inputTopic: InputTopic,
    outputTopicFailure: OutputTopic,
  )(implicit
    valueDeserializer: Deserializer[F, IN],
  ): fs2.Stream[F, Unit] = {
    import cats.implicits._
    import fs2.kafka._
    log.info(s"Running $concurrency concurrency reading from topic ${inputTopic.name}")
    kafkaConsumer
      .consumerStream
      .subscribeTo(inputTopic.name)
      .records
      .chunks
      .mapAsync(concurrency) { chunk =>
        val batch = CommittableOffsetBatch.fromFoldableMap(chunk)(_.offset)
        val processed = write(resources, chunk.toList.map(_.record.value))
          .flatMap {
            case Right(bulkWriteResult) =>
              if (bulkWriteResult.wasAcknowledged()) Sync[F].pure()
              else Sync[F].raiseError(new IllegalStateException("Mongodb did not acknowledge write of chunk")).asInstanceOf[F[Unit]]
            case Left(throwable) =>
              log.error(s"$name: Write failed: ${throwable.getMessage}")
              Sync[F].raiseError(throwable).asInstanceOf[F[Unit]]
          }

        Sync[F].delay(
          log.info(s"$name: Writing batch of size ${chunk.size}: ${
            batch.offsets.lastOption.map(t => s"${t._1}:${t._2.offset()}"
            ).getOrElse("No offset")
          }")) *> processed *> batch.commit
      }
  }

  def write[F[_] : Sync : Async](
    resources: Resources[F],
    data: List[IN],
  ): F[Either[Throwable, BulkWriteResult]]

}
