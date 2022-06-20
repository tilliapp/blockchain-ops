package app.tilli.api.utils

import app.tilli.logging.Logging
import app.tilli.serializer.KeyConverter
import cats.effect.Sync
import cats.implicits._
import io.circe.{Decoder, Encoder}
import org.http4s.client.Client
import org.http4s.{EntityDecoder, Headers, Request, Uri}

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.DurationInt

trait HttpClientErrorTrait extends Throwable {
  def message: String

  def detail: Option[String]

  def code: Option[String]

  def reason: Option[String]

  def headers: Option[Headers]

  def url: Option[String]
}

case class HttpClientError(
  override val message: String,
  override val detail: Option[String],
  override val code: Option[String],
  override val reason: Option[String],
  override val headers: Option[Headers],
  override val url: Option[String],
) extends HttpClientErrorTrait

object HttpClientError {

  def apply(e: Throwable): HttpClientError =
    new HttpClientError(
      message = e.getMessage,
      detail = None,
      code = None,
      reason = Option(e.getCause).filter(_ != null).map(_.getMessage),
      headers = None,
      url = None,
    )

}

object SimpleHttpClient extends Logging {

  def call[F[_] : Sync, A, B](
    host: String,
    path: String,
    queryParams: Map[String, String],
    conversion: A => B,
    headers: Headers = Headers.empty,
  )(implicit
    client: Client[F],
    entityDecoder: EntityDecoder[F, String],
    decoder: Decoder[A],
  ): F[Either[HttpClientErrorTrait, B]] = {
    val Right(baseUri) = Uri.fromString(s"$host/$path")
    val uri = baseUri.withQueryParams(queryParams)

    val call = {
      if (headers.isEmpty) client.expectOr[String](uri) _
      else client.expectOr[String](Request[F](uri = uri, headers = headers)) _
    }

    call { err =>
      err.body.compile.toList.map(bytes => new String(bytes.toArray, StandardCharsets.UTF_8))
        .flatMap(errorMessage =>
          Sync[F].delay(log.error(s"Error while calling endpoint ${uri.renderString}: ${err.toString()}: $errorMessage")) *>
            Sync[F].pure(
              HttpClientError(
                message = s"Error while calling endpoint: $errorMessage",
                detail = Option(err.toString()).filter(s => s != null && s.nonEmpty),
                code = Option(err.status.code.toString).filter(s => s != null && s.nonEmpty),
                reason = Option(err.status.reason).filter(s => s != null && s.nonEmpty),
                headers = Option(err.headers),
                url = Option(uri.renderString).filter(s => s != null && s.nonEmpty)
              )
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
        case error: HttpClientError => error
        case e => HttpClientError(e)
      })
  }

  def callPaged[F[_] : Sync, A, B](
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
    client: Client[F],
    entityDecoder: EntityDecoder[F, String],
    decoder: Decoder[A],
    encoder: Encoder[B],
  ): F[List[Either[HttpClientErrorTrait, B]]] = {
    val stream: fs2.Stream[F, Either[HttpClientErrorTrait, B]] =
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
