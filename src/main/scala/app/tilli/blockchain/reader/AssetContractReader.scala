package app.tilli.blockchain.reader

import app.tilli.BlazeServer
import app.tilli.api.utils.{BlazeHttpClient, HttpClientConfig}
import cats.effect.{Async, ExitCode, IO, IOApp}
import org.http4s.client.Client

case class Resources(
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
      httpClient <- BlazeHttpClient.clientWithRetry(httpClientSettings)
    } yield Resources(httpClient)

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
