package app.tilli.blockchain.service.mongodbsink

import app.tilli.BlazeServer
import app.tilli.api.utils.BlazeHttpClient
import app.tilli.blockchain.codec.BlockchainCodec._
import app.tilli.blockchain.config.AppConfig.readerAppConfig
import app.tilli.persistence.mongodb.MongoDbAdapter
import app.tilli.utils.ApplicationConfig
import cats.effect._
import io.circe.Json

object MongoSinkService extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    implicit val async = Async[IO]
    val concurrent = Concurrent[IO]

    val sslConfig = Map(
      "security.protocol" -> "SSL",
      "ssl.truststore.location" -> "/Users/andersschwartz/code/data/aiven/client.truststore-14236929421944531078.jks",
      "ssl.truststore.password" -> "a1cd60a4e89d436c913dc996bf40d6ca",
      "ssl.keystore.type" -> "PKCS12",
      "ssl.keystore.location" -> "/Users/andersschwartz/code/data/aiven/client.keystore-12010477626053255492.p12",
      "ssl.keystore.password" -> "fd13542854dd47d7bbfb774b32caf261",
      "ssl.key.password" -> "fd13542854dd47d7bbfb774b32caf261",
      "ssl.endpoint.identification.algorithm" -> "",
    )

    val mongoDbName = "tilli"
    val collectionName = "transaction_timeseries"

    import mongo4cats.circe._
    val resources = for {
      appConfig <- ApplicationConfig()
      httpClient <- BlazeHttpClient.clientWithRetry(appConfig.httpClientConfig)
      mongoClient <- MongoDbAdapter.resource(appConfig.mongoDbConfig.url)
      mongoDatabase <- Resource.eval(mongoClient.getDatabase(mongoDbName))
      transactionCollection <- Resource.eval(mongoDatabase.getCollectionWithCodec[Json](collectionName))
    } yield Resources[IO](
      appConfig = appConfig,
      sslConfig = Some(sslConfig),
      httpClient = httpClient,
      mongoClient = mongoClient,
      mongoDatabase = mongoDatabase,
      transactionCollection = transactionCollection,
    )

    resources.use { implicit r =>
      httpServer &>
        MongoDbSink.streamToSink(r)
    }.as(ExitCode.Success)

  }

  def httpServer[F[_] : Async](implicit r: Resources[F]): F[Unit] =
    BlazeServer
      .serverWithHealthCheck()
      .serve
      .compile
      .drain


}
