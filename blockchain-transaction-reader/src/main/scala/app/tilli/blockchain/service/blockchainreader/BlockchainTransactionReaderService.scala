package app.tilli.blockchain.service.blockchainreader

import app.tilli.BlazeServer
import app.tilli.api.utils.BlazeHttpClient
import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.dataprovider.ColaventHqDataProvider
import app.tilli.blockchain.service.blockchainreader
import app.tilli.blockchain.service.blockchainreader.config.AppConfig.readerAppConfig
import app.tilli.collection.{AddressRequestCache, MemCache}
import app.tilli.persistence.kafka.SslConfig
import app.tilli.persistence.mongodb.MongoDbAdapter
import app.tilli.utils.ApplicationConfig
import cats.effect._
import upperbound.Limiter

import scala.concurrent.duration.DurationInt

object BlockchainTransactionReaderService extends IOApp {

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

    import app.tilli.blockchain.codec.BlockchainCodec._
    import app.tilli.blockchain.codec.BlockchainMongodbCodec._
    val resources = for {
      appConfig <- ApplicationConfig()
      httpClient <- BlazeHttpClient.clientWithRetry(appConfig.httpClientConfig)
      covalentHqRateLimiter <- Limiter.start[IO](
        minInterval = appConfig.rateLimitCovalentHq.minIntervalMs.milliseconds,
        maxConcurrent = appConfig.rateLimitCovalentHq.maxConcurrent,
        maxQueued = appConfig.rateLimitCovalentHq.maxQueued,
      )
      covalentHqApi <- Resource.eval(IO(new ColaventHqDataProvider[IO](httpClient, concurrent)))

      mongoClient <- MongoDbAdapter.resource(appConfig.mongoDbConfig.url)
      mongoDatabase <- Resource.eval(mongoClient.getDatabase(appConfig.mongoDbConfig.db))
      dataProviderCursorCollection <- Resource.eval(mongoDatabase.getCollectionWithCodec[DataProviderCursorRecord](appConfig.mongoDbCollectionDataProviderCursor))
      addressRequestCacheCollection <- Resource.eval(mongoDatabase.getCollectionWithCodec[AddressRequestRecord](appConfig.mongoDbCollectionAddressRequestCache))

      addressRequestMemCacheTransactions <- MemCache.resource[IO, String, AddressRequest](duration = 5.minutes)
      addressRequestCacheTransactions = new AddressRequestCache[IO](addressRequestMemCacheTransactions, addressRequestCacheCollection)

      dataProviderCursorCache <- MemCache.resource[IO, String, DataProviderCursor](duration = 5.minutes)

      convertedSslConfig <- Resource.eval(IO(SslConfig.processSslConfig(sslConfig)))

    } yield blockchainreader.Resources(
      appConfig = appConfig,
      httpServerPort = appConfig.httpServerPort,
      sslConfig = Some(convertedSslConfig),
      httpClient = httpClient,
      covalentHqRateLimiter = covalentHqRateLimiter,
      transactionEventSource = covalentHqApi,
      addressRequestCacheTransactions = addressRequestCacheTransactions,
      dataProviderCursorCache = dataProviderCursorCache,
      dataProviderCursorCollection = dataProviderCursorCollection,
    )

    resources.use { implicit r =>
      httpServer &>
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
