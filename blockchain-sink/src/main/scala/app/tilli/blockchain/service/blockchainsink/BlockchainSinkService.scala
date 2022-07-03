package app.tilli.blockchain.service.blockchainsink

import app.tilli.BlazeServer
import app.tilli.blockchain.codec.BlockchainClasses.{DataProviderCursorRecord, TransactionRecord}
import app.tilli.blockchain.codec.BlockchainCodec._
import app.tilli.blockchain.service.blockchainsink.config.AppConfig.readerAppConfig
import app.tilli.persistence.kafka.SslConfig
import app.tilli.persistence.mongodb.MongoDbAdapter
import app.tilli.utils.ApplicationConfig
import cats.effect._

object BlockchainSinkService extends IOApp {

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
      mongoClient <- MongoDbAdapter.resource(appConfig.mongoDbConfig.url)
      mongoDatabase <- Resource.eval(mongoClient.getDatabase(appConfig.mongoDbConfig.db))
      transactionCollection <- Resource.eval(mongoDatabase.getCollectionWithCodec[TransactionRecord](appConfig.mongoDbCollectionTransaction))
      dataProviderCursorCollection <- Resource.eval(mongoDatabase.getCollectionWithCodec[DataProviderCursorRecord](appConfig.mongoDbCollectionDataProviderCursor))
      convertedSslConfig <- Resource.eval(IO(SslConfig.processSslConfig(sslConfig)))
    } yield Resources[IO](
      appConfig = appConfig,
      httpServerPort = appConfig.httpServerPort,
      sslConfig = Some(convertedSslConfig),
      mongoClient = mongoClient,
      mongoDatabase = mongoDatabase,
      transactionCollection = transactionCollection,
      dataProviderCursorCollection = dataProviderCursorCollection,
    )

    resources.use { implicit r =>
      httpServer &>
        BlockchainSink.streamToSink(r)
    }.as(ExitCode.Success)

  }

  def httpServer[F[_] : Async](implicit r: Resources[F]): F[Unit] =
    BlazeServer
      .serverWithHealthCheck(r.httpServerPort)
      .serve
      .compile
      .drain

}
