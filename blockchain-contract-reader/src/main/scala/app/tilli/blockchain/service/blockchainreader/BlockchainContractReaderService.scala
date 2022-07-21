package app.tilli.blockchain.service.blockchainreader

import app.tilli.BlazeServer
import app.tilli.api.utils.BlazeHttpClient
import app.tilli.blockchain.api.internal.SubmitAssetContractRequest
import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.dataprovider.{EtherscanDataProvider, OpenSeaApiDataProvider}
import app.tilli.blockchain.service.blockchainreader
import app.tilli.blockchain.service.blockchainreader.config.AppConfig.readerAppConfig
import app.tilli.collection.{AddressRequestCache, AssetContractCache, MemCache}
import app.tilli.integration.kafka.KafkaSslConfig.sslConfig
import app.tilli.persistence.kafka.SslConfig
import app.tilli.persistence.mongodb.MongoDbAdapter
import app.tilli.utils.ApplicationConfig
import cats.effect._
import cats.syntax.all._
import org.http4s.HttpRoutes
import upperbound.Limiter

import scala.concurrent.duration.DurationInt

object BlockchainContractReaderService extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    implicit val async = Async[IO]
    val concurrent = Concurrent[IO]

    import app.tilli.blockchain.codec.BlockchainMongodbCodec._
    val resources = for {
      appConfig <- ApplicationConfig()
      httpClient <- BlazeHttpClient.clientWithRetry(appConfig.httpClientConfig)
      openSeaRateLimiter <- Limiter.start[IO](
        minInterval = appConfig.rateLimitOpenSea.minIntervalMs.milliseconds,
        maxConcurrent = appConfig.rateLimitOpenSea.maxConcurrent,
        maxQueued = appConfig.rateLimitOpenSea.maxQueued,
      )
      openSeaApi <- Resource.eval(IO(new OpenSeaApiDataProvider[IO](httpClient, concurrent)))
      etherscanRateLimiter <- Limiter.start[IO](
        minInterval = appConfig.rateLimitEtherscan.minIntervalMs.milliseconds,
        maxConcurrent = appConfig.rateLimitEtherscan.maxConcurrent,
        maxQueued = appConfig.rateLimitEtherscan.maxQueued,
      )
      etherscanApi <- Resource.eval(IO(new EtherscanDataProvider[IO](httpClient, concurrent)))

      mongoClient <- MongoDbAdapter.resource(appConfig.mongoDbConfig.url)
      mongoDatabase <- Resource.eval(mongoClient.getDatabase(appConfig.mongoDbConfig.db))
      dataProviderCursorCollection <- Resource.eval(mongoDatabase.getCollectionWithCodec[DataProviderCursorRecord](appConfig.mongoDbCollectionDataProviderCursor))
      addressRequestCacheCollection <- Resource.eval(mongoDatabase.getCollectionWithCodec[AddressRequestRecord](appConfig.mongoDbCollectionAddressRequestCache))
      assetContractCollection <- Resource.eval(mongoDatabase.getCollectionWithCodec[TilliAssetContractEvent](appConfig.mongoDbCollectionAssetContract))

      addressTypeCache <- MemCache.resource[IO, String, AddressSimple](duration = 365.days)

      addressRequestMemCache <- MemCache.resource[IO, String, AddressRequest](duration = 5.minutes)
      addressRequestCache = new AddressRequestCache[IO](addressRequestMemCache, addressRequestCacheCollection)

      dataProviderCursorCache <- MemCache.resource[IO, String, DataProviderCursor](duration = 5.minutes)

      assetContractMemCache <- MemCache.resource[IO, String, String](duration = 2.hours)
      assetContractCache = new AssetContractCache[IO](assetContractMemCache, assetContractCollection)

      convertedSslConfig <- Resource.eval(IO(SslConfig.processSslConfig(sslConfig)))

    } yield blockchainreader.Resources(
      appConfig = appConfig,
      httpServerPort = appConfig.httpServerPort,
      sslConfig = Some(convertedSslConfig),
      httpClient = httpClient,
      openSeaRateLimiter = openSeaRateLimiter,
      etherscanRateLimiter = etherscanRateLimiter,
      assetContractSource = openSeaApi,
      assetContractEventSource = openSeaApi,
      assetContractTypeSource = etherscanApi,
      addressTypeCache = addressTypeCache,
      addressRequestCache = addressRequestCache,
      dataProviderCursorCache = dataProviderCursorCache,
      dataProviderCursorCollection = dataProviderCursorCollection,
      assetContractCache = assetContractCache,
    )

    //    val endpoints = Seq(
    //      SubmitAssetContractRequest.endpoint
    //    )
    //    val swaggerRoute = new SwaggerHttp4s(
    //      yaml = OpenAPIDocsInterpreter()
    //        .toOpenAPI(endpoints, title = "tilli API", version = "v1")
    //        .toYaml,
    //    ).routes[IO]

    resources.use { implicit r =>
      import app.tilli.blockchain.codec.BlockchainCodec._
      val routes = List(
        SubmitAssetContractRequest.service(r),
      ).reduce((a, b) => a <+> b)
      httpServer(routes = Some(routes)) &>
        AssetContractReader.assetContractRequestsStream(r) &>
        AssetContractEventReader.assetContractEventRequestStream(r) &>
        AddressFilter.addressFilterStream(r) &>
        TransactionAssetContractFilter.assetContractRequestsStream(r)
    }.as(ExitCode.Success)
  }


  def httpServer[F[_] : Async](
    routes: Option[HttpRoutes[F]] = None,
  )(implicit r: Resources): F[Unit] =
    BlazeServer
      .serverWithHealthCheck(
        httpPort = r.httpServerPort,
        routes = routes,
      )
      .serve
      .compile
      .drain
}
