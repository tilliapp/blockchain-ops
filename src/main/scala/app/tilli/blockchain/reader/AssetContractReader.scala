package app.tilli.blockchain.reader

import app.tilli.BlazeServer
import app.tilli.api.utils.{BlazeHttpClient, HttpClientConfig}
import app.tilli.blockchain.codec.BlockchainCodec._
import app.tilli.blockchain.config.AppConfig
import app.tilli.blockchain.config.AppConfig.readerAppConfig
import app.tilli.persistence.kafka.KafkaConsumer
import app.tilli.utils.ApplicationConfig
import cats.effect.{Async, ExitCode, IO, IOApp}
import io.circe.Json
import org.http4s.client.Client

import java.util.UUID

case class Resources(
  appConfig: AppConfig,
  kafkaConsumer: fs2.kafka.KafkaConsumer[IO, Option[UUID], Json],
  httpClient: Client[IO],
)

object AssetContractReader extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {

    implicit val async = Async[IO]

    val httpClientSettings = HttpClientConfig(
      connectTimeoutSecs = 30,
      requestTimeoutSecs = 30,
      maxRetryWaitMilliSecs = 60000,
      maxRetries = 20,
    )

    val resources = for {
      appConfig <- ApplicationConfig()
      httpClient <- BlazeHttpClient.clientWithRetry(httpClientSettings)
      kafkaConsumer <- new KafkaConsumer[Option[UUID], Json](appConfig.kafkaConsumerConfiguration).consumerResource
    } yield Resources(appConfig, kafkaConsumer, httpClient)

    resources.use { r =>

      BlazeServer
        .serverWithHealthCheck()
        .serve
        .compile
        .drain
        .as(ExitCode.Success)
    }

  }

}
