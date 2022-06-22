package app.tilli.blockchain.service

import app.tilli.BlazeServer
import app.tilli.api.utils.BlazeHttpClient
import app.tilli.blockchain.codec.BlockchainCodec._
import app.tilli.blockchain.config.AppConfig.readerAppConfig
import app.tilli.blockchain.dataprovider._
import app.tilli.utils.ApplicationConfig
import cats.effect._
import upperbound.Limiter

import scala.concurrent.duration.DurationLong

object Service extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    implicit val async = Async[IO]
    val concurrent = Concurrent[IO]

    val resources = for {
      appConfig <- ApplicationConfig()
      httpClient <- BlazeHttpClient.clientWithRetry(appConfig.httpClientConfig)
      openSeaRateLimiter <- Limiter.start[IO](
        minInterval = appConfig.rateLimitOpenSea.minIntervalMs.milliseconds,
        maxConcurrent = appConfig.rateLimitOpenSea.maxConcurrent,
        maxQueued = appConfig.rateLimitOpenSea.maxQueued,
      )
      openSeaApi <- Resource.eval(IO(new OpenSeaApi[IO](httpClient, concurrent)))
      covalentHqRateLimiter <-Limiter.start[IO](
        minInterval = appConfig.rateLimitCovalentHq.minIntervalMs.milliseconds,
        maxConcurrent = appConfig.rateLimitCovalentHq.maxConcurrent,
        maxQueued = appConfig.rateLimitCovalentHq.maxQueued,
      )
      covalentHqApi <- Resource.eval(IO(new ColaventHqDataProvider[IO](httpClient, concurrent)))
    } yield Resources(
      appConfig = appConfig,
      httpClient = httpClient,
      openSeaRateLimiter = openSeaRateLimiter,
      covalentHqRateLimiter = covalentHqRateLimiter,
      assetContractSource = openSeaApi,
      assetContractEventSource = openSeaApi,
      transactionEventSource = covalentHqApi
    )

    resources.use { implicit r =>
      httpServer &>
        AssetContractReader.assetContractRequestsStream(r) &>
        AssetContractEventReader.assetContractEventRequestStream(r) &>
        AddressFilter.addressFilterStream(r)
//      &>
//        TransactionEventReader.transactionEventRequestStream(r)
    }.as(ExitCode.Success)
  }

  def httpServer[F[_] : Async](implicit r: Resources): F[Unit] =
    BlazeServer
      .serverWithHealthCheck()
      .serve
      .compile
      .drain
}
