package app.tilli.blockchain.codec

import app.tilli.blockchain.codec.BlockchainClasses.{AddressRequest, AddressRequestRecord, AssetContractHolderRequest, DataProvider, DataProviderCursor, DataProviderCursorRecord, Header, HttpClientError, Origin, TilliDataProviderError, TilliJsonEvent, TransactionEventsResult, TransactionRecord, TransactionRecordData}
import app.tilli.blockchain.codec.BlockchainConfig.EventType
import io.circe.Codec
import io.circe.Decoder.decodeEnumeration
import io.circe.Encoder.encodeEnumeration
import io.circe.generic.semiauto.deriveCodec
import mongo4cats.circe.MongoJsonCodecs

object BlockchainMongodbCodec extends MongoJsonCodecs {

  implicit lazy val codecHeader: Codec[Header] = deriveCodec
  implicit lazy val codecOrigin: Codec[Origin] = deriveCodec
  implicit lazy val codecTilliJsonEvent: Codec[TilliJsonEvent] = deriveCodec
  implicit lazy val codecTilliDataProviderError: Codec[TilliDataProviderError] = deriveCodec

  implicit lazy val codecAssetContractHolderRequest: Codec[AssetContractHolderRequest] = deriveCodec
  implicit lazy val codecAddressRequest: Codec[AddressRequest] = deriveCodec
  implicit lazy val codecDataProvider: Codec[DataProvider] = deriveCodec
  implicit lazy val codecDataProviderCursor: Codec[DataProviderCursor] = deriveCodec
  implicit lazy val codecTransactionEventsResult: Codec[TransactionEventsResult] = deriveCodec

  implicit lazy val codecTransactionRecordData: Codec[TransactionRecordData] = deriveCodec

  implicit lazy val codecHttpClientError: Codec[HttpClientError] = deriveCodec

  implicit lazy val decoderEventType = decodeEnumeration(EventType)
  implicit lazy val encoderEventType = encodeEnumeration(EventType)

  //  implicit val decodeInstantFromString: Decoder[Instant] = Decoder.decodeString.emapTry { str =>
  //    Try(Instant.parse(str))
  //  }
  implicit lazy val codecAddressRequestRecord: Codec[AddressRequestRecord] = deriveCodec
  implicit lazy val codecTransactionRecord: Codec[TransactionRecord] = deriveCodec
  implicit lazy val codecDataProviderCursorRecord: Codec[DataProviderCursorRecord] = deriveCodec

}

//object BlockchainMongodbCodec extends BlockchainMongodbCodec