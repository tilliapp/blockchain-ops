package app.tilli.serializer

import cats.effect.Sync
import fs2.kafka.Deserializer
import io.circe
import io.circe.parser.parse
import io.circe.{Decoder, Json}

import java.nio.charset.StandardCharsets

object Fs2KafkaCodec {

  def decodeToJson(bytes: Array[Byte])(implicit d: Decoder[Json]): Either[Throwable, Json] =
    parse(new String(bytes, StandardCharsets.UTF_8))

  def decodeToClass[A](bytes: Array[Byte])(implicit d: Decoder[A]): Either[Throwable, A] =
    for {
      json <- parse(new String(bytes, StandardCharsets.UTF_8))
      obj <- json.as[A]
    } yield obj

  def classDeserializer[F[_], A](implicit
    decoder: Decoder[A],
    sync: Sync[F]
  ): Deserializer[F, A] =
    Deserializer.lift { bytes =>
      decodeToClass(bytes)(decoder) match {
        case Right(o) => sync.pure(o)
        case Left(e) => sync.raiseError(e)
      }
    }

  def jsonDeserializer[F[_]](implicit
    sync: Sync[F]
  ): Deserializer[F, Json] =
    Deserializer.lift { bytes =>
      decodeToJson(bytes) match {
        case Right(json) => sync.pure(json)
        case Left(e) => sync.raiseError(e)
      }
    }

}
