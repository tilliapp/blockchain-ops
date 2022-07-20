package app.tilli.blockchain.analytics.service

import app.tilli.BlazeServer
import app.tilli.blockchain.analytics.service.config.AppConfig.readerAppConfig
import app.tilli.blockchain.codec.BlockchainClasses.{Doc, TransactionRecord}
import app.tilli.persistence.mongodb.MongoDbAdapter
import app.tilli.utils.ApplicationConfig
import cats.effect._
import org.http4s.HttpRoutes

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

    } yield Resources(
      appConfig = appConfig,
      httpServerPort = appConfig.httpServerPort,
      transactionCollection = transactionCollection,
    )

    resources.use { implicit r =>

      httpServer(routes = None) &>
        NftHolding.stream(r)
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
