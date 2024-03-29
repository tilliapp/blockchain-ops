package app.tilli.blockchain.service.analytics

import app.tilli.BlazeServer
import app.tilli.blockchain.service.analytics.config.AppConfig.readerAppConfig
import app.tilli.blockchain.codec.BlockchainClasses.{Doc, TilliAssetContractEvent}
import app.tilli.blockchain.service.analytics
import app.tilli.collection.{AssetContractCache, MemCache}
import app.tilli.integration.kafka.KafkaSslConfig.sslConfig
import app.tilli.persistence.kafka.SslConfig
import app.tilli.persistence.mongodb.MongoDbAdapter
import app.tilli.serializer.Fs2KafkaCodec.serializer
import app.tilli.utils.ApplicationConfig
import cats.effect._
import org.http4s.HttpRoutes

import scala.concurrent.duration.DurationInt

object AnalyticsService extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    implicit val async = Async[IO]
    val concurrent = Concurrent[IO]

    import app.tilli.blockchain.codec.BlockchainMongodbCodec._
    val resources = for {
      appConfig <- ApplicationConfig()
      mongoClient <- MongoDbAdapter.resource(appConfig.mongoDbConfig.url)
      mongoDatabase <- Resource.eval(mongoClient.getDatabase(appConfig.mongoDbConfig.db))
      transactionCollection <- Resource.eval(mongoDatabase.getCollectionWithCodec[Doc](appConfig.mongoDbCollectionTransaction))
      convertedSslConfig <- Resource.eval(IO(SslConfig.processSslConfig(sslConfig)))

      assetContractCollection <- Resource.eval(mongoDatabase.getCollectionWithCodec[TilliAssetContractEvent](appConfig.mongoDbCollectionAssetContract))
      assetContractMemCache <- MemCache.resource[IO, String, String](duration = 2.hours)
      assetContractCache = new AssetContractCache[IO](assetContractMemCache, assetContractCollection)
    } yield analytics.Resources[IO](
      appConfig = appConfig,
      httpServerPort = appConfig.httpServerPort,
      sslConfig = Some(convertedSslConfig),
      transactionCollection = transactionCollection,
      assetContractCache = assetContractCache,
    )

    resources.use { implicit r =>
      import app.tilli.blockchain.codec.BlockchainCodec._
      httpServer(routes = None) &>
        NftHolding.stream(r) &>
        AnalyticsTrigger.stream(r)
    }.as(ExitCode.Success)

  }

  def httpServer[F[_] : Async](
    routes: Option[HttpRoutes[F]] = None,
  )(implicit r: Resources[F]): F[Unit] =
    BlazeServer
      .serverWithHealthCheck(
        httpPort = r.httpServerPort,
        routes = routes,
      )
      .serve
      .compile
      .drain

}
