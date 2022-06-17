package app.tilli.blockchain.codec

import app.tilli.blockchain.codec.BlockchainClasses.{Header, Origin, TilliJsonEvent}
import app.tilli.serializer.Fs2KafkaCodec
import app.tilli.serializer.Fs2KafkaCodec.jsonDeserializer
import cats.effect.IO
import fs2.kafka.{Deserializer, RecordSerializer, Serializer}
import io.circe.{Codec, Json}
import io.circe.generic.semiauto.deriveCodec

object BlockchainCodec {

  // Circe
//  implicit lazy val codecJson: Codec[Json] = deriveCodec
  implicit lazy val codecHeader: Codec[Header] = deriveCodec
  implicit lazy val codecOrigin: Codec[Origin] = deriveCodec
  implicit lazy val codecTilliJsonEvent: Codec[TilliJsonEvent] = deriveCodec

  // FS kafka
  implicit val deserializerJson: Deserializer[IO, Json] = jsonDeserializer
  implicit val serializerJson: Serializer[IO, Json] = Fs2KafkaCodec.serializer

  implicit val serializerTilliJsonEvent: Serializer[IO, TilliJsonEvent] = Fs2KafkaCodec.serializer


}
