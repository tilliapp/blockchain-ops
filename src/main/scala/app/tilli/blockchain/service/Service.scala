package app.tilli.blockchain.service

import app.tilli.BlazeServer
import app.tilli.api.utils.BlazeHttpClient
import app.tilli.blockchain.codec.BlockchainClasses.AddressSimple
import app.tilli.blockchain.codec.BlockchainCodec._
import app.tilli.blockchain.config.AppConfig.readerAppConfig
import app.tilli.blockchain.dataprovider._
import app.tilli.collection.MemCache
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
      covalentHqRateLimiter <- Limiter.start[IO](
        minInterval = appConfig.rateLimitCovalentHq.minIntervalMs.milliseconds,
        maxConcurrent = appConfig.rateLimitCovalentHq.maxConcurrent,
        maxQueued = appConfig.rateLimitCovalentHq.maxQueued,
      )
      etherscanRateLimiter <- Limiter.start[IO](
        minInterval = appConfig.rateLimitEtherscan.minIntervalMs.milliseconds,
        maxConcurrent = appConfig.rateLimitEtherscan.maxConcurrent,
        maxQueued = appConfig.rateLimitEtherscan.maxQueued,
      )
      covalentHqApi <- Resource.eval(IO(new ColaventHqDataProvider[IO](httpClient, concurrent)))
      etherscanApi <- Resource.eval(IO(new EtherscanDataProvider[IO](httpClient, concurrent)))
      cache <- MemCache.resource[IO, String, AddressSimple](duration = 10.minutes)
    } yield Resources(
      appConfig = appConfig,
      httpClient = httpClient,
      openSeaRateLimiter = openSeaRateLimiter,
      covalentHqRateLimiter = covalentHqRateLimiter,
      etherscanRateLimiter = etherscanRateLimiter,
      assetContractSource = openSeaApi,
      assetContractEventSource = openSeaApi,
      transactionEventSource = covalentHqApi,
      assetContractTypeSource = etherscanApi,
      addressCache = cache,
    )

    resources.use { implicit r =>
      httpServer &>
        AssetContractReader.assetContractRequestsStream(r) &>
        AssetContractEventReader.assetContractEventRequestStream(r) &>
        AddressFilter.addressFilterStream(r) &>
        TransactionEventReader.transactionEventRequestStream(r)
    }.as(ExitCode.Success)
  }

  def httpServer[F[_] : Async](implicit r: Resources): F[Unit] =
    BlazeServer
      .serverWithHealthCheck()
      .serve
      .compile
      .drain
}
