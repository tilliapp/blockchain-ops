package app.tilli

import cats.syntax.all._

import app.tilli.api.endpoint.HealthCheckEndpoint
import cats.effect.Resource
import cats.effect.kernel.Async
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Server

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait BlazeServer {

  def server[F[_] : Async](
    interface: String = "0.0.0.0",
    httpPort: Int = 8080,
    threads: Int = 4,
    responseHeaderTimeout: Duration = 30.seconds,
    routes: HttpRoutes[F],
  ): BlazeServerBuilder[F] = {
    val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(threads))
    BlazeServerBuilder[F]
      .withExecutionContext(ec)
      .bindHttp(httpPort, interface)
      .withResponseHeaderTimeout(responseHeaderTimeout)
      .withHttpApp(routes.orNotFound)
    //      .withSslContext() // Coming one day...
  }

  def serverWithHealthCheck[F[_] : Async](
    httpPort: Int = 8080,
    routes: Option[HttpRoutes[F]] = None,
  ): BlazeServerBuilder[F] = server(
    httpPort = httpPort,
    routes =
      List(
        Some(HealthCheckEndpoint.routes), // TODO: Add routes as input to function and join here.
        routes,
      ).flatten.reduce((a, b) => a <+> b)
  )
}

object BlazeServer extends BlazeServer
