package app.tilli.blockchain.codec

import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.codec.BlockchainConfig.EventType
import app.tilli.serializer.Fs2KafkaCodec
import app.tilli.serializer.Fs2KafkaCodec.{classDeserializer, jsonDeserializer}
import cats.effect.IO
import fs2.kafka.{Deserializer, Serializer}
import io.circe.Decoder.decodeEnumeration
import io.circe.Encoder.encodeEnumeration
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Json}

object BlockchainCodec {

  // Circe
//  implicit lazy val codecJson: Codec[Json] = deriveCodec
  implicit lazy val codecHeader: Codec[Header] = deriveCodec
  implicit lazy val codecOrigin: Codec[Origin] = deriveCodec
  implicit lazy val codecTilliJsonEvent: Codec[TilliJsonEvent] = deriveCodec
  implicit lazy val codecAssetContractHolderRequest: Codec[AssetContractHolderRequest] = deriveCodec
  implicit lazy val codecAddressRequest: Codec[AddressRequest] = deriveCodec

  implicit lazy val decoderEventType = decodeEnumeration(EventType)
  implicit lazy val encoderEventType = encodeEnumeration(EventType)

  // FS kafka
  implicit val deserializerJson: Deserializer[IO, Json] = jsonDeserializer
  implicit val deserializerTilliJsonEvent: Deserializer[IO, TilliJsonEvent] = classDeserializer

  implicit val serializerJson: Serializer[IO, Json] = Fs2KafkaCodec.serializer
  implicit val serializerTilliJsonEvent: Serializer[IO, TilliJsonEvent] = Fs2KafkaCodec.serializer


}
