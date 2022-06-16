package app.tilli.blockchain.reader

import app.tilli.BlazeServer
import app.tilli.api.utils.{BlazeHttpClient, HttpClientConfig}
import app.tilli.blockchain.codec.BlockchainCodec._
import app.tilli.blockchain.config.AppConfig
import app.tilli.blockchain.config.AppConfig.readerAppConfig
import app.tilli.persistence.kafka.KafkaConsumer
import app.tilli.utils.{ApplicationConfig, InputTopic}
import cats.effect.{Async, ExitCode, IO, IOApp}
import io.circe.Json
import org.http4s.client.Client

import java.util.UUID
import scala.concurrent.duration.DurationLong

case class Resources(
  appConfig: AppConfig,
  httpClient: Client[IO],
)

object AssetContractReader extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    implicit val async = Async[IO]

    val resources = for {
      appConfig <- ApplicationConfig()
      httpClient <- BlazeHttpClient.clientWithRetry(appConfig.httpClientConfig)
    } yield Resources(appConfig, httpClient)

    def httpServer(implicit r: Resources): IO[ExitCode] =
      BlazeServer
        .serverWithHealthCheck()
        .serve
        .compile
        .drain
        .as(ExitCode.Success)

    def kafkaStream(implicit r: Resources): IO[ExitCode] = {
      val kafkaConfig = r.appConfig.kafkaConsumerConfiguration
      val kafkaConsumer = new KafkaConsumer[Option[UUID], Json](kafkaConfig)

      import fs2.kafka._
      def processRecord(record: ConsumerRecord[Option[UUID], Json]): IO[Unit] = IO(println(s"Processing record: $record"))
      val stream = kafkaConsumer
        .consumerStream
        .subscribeTo(r.appConfig.inputTopic.name)
        .records
//        .evalTap(r => IO(println(r.record.value)))
        .mapAsync(1) {committable =>
          processRecord(committable.record).as(committable.offset)
        }
        .through(commitBatchWithin(kafkaConfig.batchSize, kafkaConfig.batchDurationMs.milliseconds))

      stream.compile.drain.as(ExitCode.Success)
    }

    resources.use { implicit r =>
      httpServer &> kafkaStream
    }

  }

}
