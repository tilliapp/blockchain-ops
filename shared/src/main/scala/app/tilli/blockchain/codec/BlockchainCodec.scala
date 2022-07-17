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
import io.circe.{Codec, Decoder, Json}

import java.time.Instant
import scala.util.Try

object BlockchainCodec {

  // Circe
  implicit lazy val decoderEventType = decodeEnumeration(EventType)
  implicit lazy val encoderEventType = encodeEnumeration(EventType)

  implicit lazy val codecOrigin: Codec[Origin] = deriveCodec
  implicit lazy val codecHeader: Codec[Header] = deriveCodec

  implicit lazy val codecTilliJsonEvent: Codec[TilliJsonEvent] = deriveCodec
  implicit lazy val codecTilliDataProviderError: Codec[TilliDataProviderError] = deriveCodec
  implicit lazy val codecAssetContractHolderRequest: Codec[AssetContractHolderRequest] = deriveCodec
  implicit lazy val codecAddressRequest: Codec[AddressRequest] = deriveCodec
  implicit lazy val codecDataProvider: Codec[DataProvider] = deriveCodec
  implicit lazy val codecDataProviderCursor: Codec[DataProviderCursor] = deriveCodec
  implicit lazy val codecTransactionEventsResult: Codec[TransactionEventsResult] = deriveCodec

  implicit lazy val codecTransactionRecordData: Codec[TransactionRecordData] = deriveCodec
  implicit lazy val codecHttpClientError: Codec[HttpClientError] = deriveCodec

  implicit lazy val codecAssetContract: Codec[AssetContract] = deriveCodec
  implicit lazy val codecAssetContractRequest: Codec[AssetContractRequest] = deriveCodec
  implicit lazy val codecTilliAssetContractEvent: Codec[TilliAssetContractEvent] = deriveCodec

  // FS kafka
  implicit val deserializerJson: Deserializer[IO, Json] = jsonDeserializer
  implicit val deserializerTilliJsonEvent: Deserializer[IO, TilliJsonEvent] = classDeserializer
  implicit val deserializerTilliAssetContractEvent: Deserializer[IO, TilliAssetContractEvent] = classDeserializer

  implicit val serializerJson: Serializer[IO, Json] = Fs2KafkaCodec.serializer
  implicit val serializerTilliJsonEvent: Serializer[IO, TilliJsonEvent] = Fs2KafkaCodec.serializer
  implicit val serializerAssetContractRequest: Serializer[IO, AssetContractRequest] = Fs2KafkaCodec.serializer
  implicit val serializerTilliDataProviderError: Serializer[IO, TilliDataProviderError] = Fs2KafkaCodec.serializer
  implicit val serializerTilliAssetContractEvent: Serializer[IO, TilliAssetContractEvent] = Fs2KafkaCodec.serializer

  object InstantFromLongDecoder {

    implicit val decodeInstantFromLong: Decoder[Instant] = Decoder.decodeLong.emapTry { str =>
      Try(Instant.ofEpochMilli(str))
    }

  }

}

//object BlockchainCodec extends BlockchainCodec