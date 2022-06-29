package app.tilli.api.utils

import app.tilli.blockchain.codec.BlockchainClasses.HttpClientError
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

object SimpleHttpClient extends Logging {

  def toUri(
    host: String,
    path: String,
    queryParams: Map[String, String],
  ): Either[Throwable, Uri] = Uri.fromString(s"$host/$path").map(_.withQueryParams(queryParams))

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
  ): F[Either[Throwable, B]] = {
    val Right(uri) = toUri(host, path, queryParams)

    val call = {
      if (headers.isEmpty) client.expectOr[String](uri) _
      else client.expectOr[String](Request[F](uri = uri, headers = headers)) _
    }

    call { err =>
      err.body.compile.toList.map(bytes => new String(bytes.toArray, StandardCharsets.UTF_8))
        .flatMap(errorMessage =>
          Sync[F].pure(
            HttpClientError(
              message = s"Error while calling endpoint: $errorMessage",
              detail = Option(err.toString()).filter(s => s != null && s.nonEmpty),
              code = Option(err.status.code),
              reason = Option(err.status.reason).filter(s => s != null && s.nonEmpty),
              headers = Option(err.headers.toString),
              url = Option(uri.renderString).filter(s => s != null && s.nonEmpty)
            )
          )
        )
    }.attempt
      .map(_
        .flatMap(s => KeyConverter.snakeCaseToCamelCase(s))
        .flatMap(s =>
          for {
            json <- io.circe.parser.parse(s)
            data <- json.as[A]
          } yield conversion(data)
        ))
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
  )(implicit
    client: Client[F],
    entityDecoder: EntityDecoder[F, String],
    decoder: Decoder[A],
    encoder: Encoder[B],
  ): F[List[Either[Throwable, B]]] = {
    val stream: fs2.Stream[F, Either[Throwable, B]] =
      fs2.Stream.unfoldLoopEval(s = "")(page => {
        import io.circe.optics.JsonPath.root
        import io.circe.syntax.EncoderOps
        val withPageMap = if (page != null && page.nonEmpty) queryParams ++ Map(cursorQueryParamKey -> page) else queryParams
        call(host, path, withPageMap, conversion, headers)
          .map { r =>
            val nextPageOption = r match {
              case Left(_) => None
              case Right(b) => root.selectDynamic(pageParamKey).string.getOption(b.asJson)
            }
            log.debug(s"Next page=$nextPageOption${uuid.map(u => s"($u ${getTimestamp()})").getOrElse("")}")
            (r, nextPageOption)
          }
      })
    stream
      .takeWhile(r => r.isRight)
      .compile
      .toList
  }

  def getTimestamp(now: Instant = Instant.now()): String = now.toString

}
