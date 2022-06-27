package app.tilli.blockchain.service.blockchainreader

import app.tilli.BlazeServer
import app.tilli.api.utils.BlazeHttpClient
import app.tilli.blockchain.codec.BlockchainClasses.AddressSimple
import app.tilli.blockchain.codec.BlockchainCodec._
import app.tilli.blockchain.dataprovider.{ColaventHqDataProvider, EtherscanDataProvider, OpenSeaApi}
import app.tilli.blockchain.service.blockchainreader
import app.tilli.blockchain.service.blockchainreader.config.AppConfig.readerAppConfig
import app.tilli.collection.MemCache
import app.tilli.persistence.kafka.SslConfig
import app.tilli.utils.ApplicationConfig
import cats.effect._
import upperbound.Limiter

import scala.concurrent.duration.DurationInt

object BlockchainReaderService extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    implicit val async = Async[IO]
    val concurrent = Concurrent[IO]

    val sslConfig = Map(
      "security.protocol" -> "SSL",
      "ssl.truststore.location" -> "gs://tilli-prod-kafka-secrets/client.truststore-14236929421944531078.jks",
      "ssl.truststore.password" -> "a1cd60a4e89d436c913dc996bf40d6ca",
      "ssl.keystore.type" -> "PKCS12",
      "ssl.keystore.location" -> "gs://tilli-prod-kafka-secrets/client.keystore-12010477626053255492.p12",
      "ssl.keystore.password" -> "fd13542854dd47d7bbfb774b32caf261",
      "ssl.key.password" -> "fd13542854dd47d7bbfb774b32caf261",
      "ssl.endpoint.identification.algorithm" -> "",
    )

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
      cache <- MemCache.resource[IO, String, AddressSimple](duration = 12.hours)

      convertedSslConfig <- Resource.eval(IO(SslConfig.processSslConfig(sslConfig)))
    } yield blockchainreader.Resources(
      appConfig = appConfig,
      httpServerPort = appConfig.httpServerPort,
      sslConfig = Some(convertedSslConfig),
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
      .serverWithHealthCheck(httpPort = r.httpServerPort)
      .serve
      .compile
      .drain
}
