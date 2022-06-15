package app.tilli.api.utils

import app.tilli.logging.Logging
import app.tilli.serializer.KeyConverter
import cats.effect.IO
import cats.implicits._
import io.circe.{Decoder, Encoder}
import org.http4s.client.Client
import org.http4s.{Headers, Request, Uri}

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.DurationInt

trait HttpClientErrorTrait {
  def message: String

  def code: Option[String]

  def reason: Option[String]

  def headers: Option[Headers]
}

case class HttpClientError(
  override val message: String,
  override val code: Option[String],
  override val reason: Option[String],
  override val headers: Option[Headers],
) extends Throwable with HttpClientErrorTrait

object HttpClientError {

  def apply(e: Throwable): HttpClientError =
    new HttpClientError(
      message = e.getMessage,
      code = None,
      reason = Option(e.getCause).filter(_ != null).map(_.getMessage),
      headers = None,
    )

}

object SimpleHttpClient extends Logging {

  def call[A, B](
    host: String,
    path: String,
    queryParams: Map[String, String],
    conversion: A => B,
    headers: Headers = Headers.empty,
  )(implicit
    client: Client[IO],
    decoder: Decoder[A],
  ): IO[Either[HttpClientErrorTrait, B]] = {
    val Right(baseUri) = Uri.fromString(s"$host/$path")
    val uri = baseUri.withQueryParams(queryParams)

    val call = {
      if (headers.isEmpty) client.expectOr[String](uri) _
      else client.expectOr[String](Request[IO](uri = uri, headers = headers)) _
    }

    call { err =>
      import cats.effect.unsafe.implicits.global
      val errorMessage = new String(err.body.compile.to(Array).unsafeRunSync()) + s": ${uri.renderString}"
      IO(log.error(errorMessage)) *> IO(
        HttpClientError(
          message = s"An error has occurred: ${err.toString()}",
          code = Option(err.status.code.toString).filter(s => s != null && s.nonEmpty),
          reason = Option(err.status.reason).filter(s => s != null && s.nonEmpty),
          headers = Option(err.headers),
        )
      )
    }
      .attempt
      .map(_
        .flatMap(s => KeyConverter.snakeCaseToCamelCase(s))
        .flatMap(s =>
          for {
            json <- io.circe.parser.parse(s)
            data <- json.as[A]
          } yield conversion(data)
        ))
      .map(_.leftMap {
        case error: HttpClientError => HttpClientError(error)
        case e => HttpClientError(e)
      })
  }

  def callPaged[A, B](
    host: String,
    path: String,
    pageParamKey: String,
    cursorQueryParamKey: String,
    queryParams: Map[String, String],
    conversion: A => B,
    headers: Headers = Headers.empty,
    uuid: Option[UUID] = None,
    sleepMs: DurationInt = 250
  )(implicit
    client: Client[IO],
    decoder: Decoder[A],
    encoder: Encoder[B],
  ): IO[List[Either[HttpClientErrorTrait, B]]] = {
    val stream: fs2.Stream[IO, Either[HttpClientErrorTrait, B]] =
      fs2.Stream.unfoldLoopEval(s = "")(page => {
        import io.circe.optics.JsonPath.root
        import io.circe.syntax.EncoderOps
        val withPageMap = if (page != null && page.nonEmpty) queryParams ++ Map(cursorQueryParamKey -> page) else queryParams
        call(host, path, withPageMap, conversion, headers)
          .map { r =>
            val obj = r
            val nextPageOption = r match {
              case Left(_) => None
              case Right(b) =>
                val json = b.asJson
                val nextPage = root.selectDynamic(pageParamKey).string.getOption(json)
                nextPage
            }
            println(s"Next page=$nextPageOption${uuid.map(u => s"($u ${getTimestamp()})").getOrElse("")}")
            (obj, nextPageOption)
          }
      })
    stream
      .takeWhile(r => r.isRight)
      .compile
      .toList
  }

  def getTimestamp(now: Instant = Instant.now()): String = now.toString

}
