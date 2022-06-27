package app.tilli.api.endpoint

import app.tilli.api.response.Response._
import app.tilli.api.utils.ApiSerdes.Serializer
import cats.effect.{Async, Sync}
import org.http4s.HttpRoutes
import sttp.tapir.generic.auto._
import sttp.tapir.server.ServerEndpoint.Full
import sttp.tapir.server.http4s.Http4sServerInterpreter

object HealthCheckEndpoint {

  import app.tilli.api.codec.ApiCodec._
  import sttp.tapir._

  lazy val endpoint: Endpoint[Unit, Unit, ErrorResponse, HealthCheckSuccess, Any] = sttp.tapir
    .endpoint
    .get
    .in("health")
    .out(Serializer.jsonBody[HealthCheckSuccess])
    .errorOut(Serializer.jsonBody[ErrorResponse])
    .name("Health Check")
    .description(s"Returns ${sttp.model.StatusCode.Ok} if healthy")
    .tags(List("Health"))

  def serverEndpoint[F[_] : Sync : Async](implicit sync: Sync[F]): Full[Unit, Unit, Unit, ErrorResponse, HealthCheckSuccess, Any, F] =
    endpoint.serverLogic(process(_)(sync))

  def routes[F[_]](implicit async: Async[F]): HttpRoutes[F] = Http4sServerInterpreter[F]().toRoutes(serverEndpoint)

  def process[F[_]](input: Unit)(implicit sync: Sync[F]): F[Either[ErrorResponse, HealthCheckSuccess]] =
    sync.delay(Right(HealthCheckSuccess()))
}
