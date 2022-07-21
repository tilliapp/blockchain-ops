package app.tilli.blockchain.service.blockchainsink

import app.tilli.BlazeServer
import app.tilli.blockchain.codec.BlockchainClasses.{DataProviderCursorRecord, TilliAssetContractEvent, TransactionRecord}
import app.tilli.blockchain.codec.BlockchainCodec._
import app.tilli.blockchain.service.blockchainsink.config.AppConfig.readerAppConfig
import app.tilli.integration.kafka.KafkaSslConfig.sslConfig
import app.tilli.persistence.kafka.SslConfig
import app.tilli.persistence.mongodb.MongoDbAdapter
import app.tilli.utils.ApplicationConfig
import cats.effect._

object BlockchainSinkService extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    implicit val async = Async[IO]
    val concurrent = Concurrent[IO]

    import app.tilli.blockchain.codec.BlockchainMongodbCodec._
    val resources = for {
      appConfig <- ApplicationConfig()
      mongoClient <- MongoDbAdapter.resource(appConfig.mongoDbConfig.url)
      mongoDatabase <- Resource.eval(mongoClient.getDatabase(appConfig.mongoDbConfig.db))
      transactionCollection <- Resource.eval(mongoDatabase.getCollectionWithCodec[TransactionRecord](appConfig.mongoDbCollectionTransaction))
      dataProviderCursorCollection <- Resource.eval(mongoDatabase.getCollectionWithCodec[DataProviderCursorRecord](appConfig.mongoDbCollectionDataProviderCursor))
      assetContractEventCollection <-Resource.eval(mongoDatabase.getCollectionWithCodec[TilliAssetContractEvent](appConfig.mongoDbCollectionAssetContract))
      convertedSslConfig <- Resource.eval(IO(SslConfig.processSslConfig(sslConfig)))
    } yield Resources[IO](
      appConfig = appConfig,
      httpServerPort = appConfig.httpServerPort,
      sslConfig = Some(convertedSslConfig),
      mongoClient = mongoClient,
      mongoDatabase = mongoDatabase,
      transactionCollection = transactionCollection,
      dataProviderCursorCollection = dataProviderCursorCollection,
      assetContractEventCollection = assetContractEventCollection,
    )

    import app.tilli.blockchain.codec.BlockchainCodec._
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
