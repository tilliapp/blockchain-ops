package app.tilli.blockchain.codec

import app.tilli.blockchain.codec.BlockchainClasses.DataProvider

import java.util.UUID

object BlockchainConfig {

  val DataTypeAssetContract = "AssetContract"
  val DataTypeAssetContractEventRequest = "AssetContractEventRequest"
  val DataTypeAssetContractEvent = "AssetContractEvent"
  val DataTypeAddressRequest = "AddressRequest"
  val DataTypeTransactionEvent = "TransactionEvent"
  val DataTypeDataProviderError = "DataProviderError"
  val DataTypeDataProviderCursor = "DataProviderCursor"

  val Version_20220617 = "2022-06-17"
  val DataTypeToVersion = Map(
    DataTypeAssetContract -> Version_20220617,
    DataTypeAssetContractEventRequest -> Version_20220617,
    DataTypeAssetContractEvent -> Version_20220617,
    DataTypeAddressRequest -> Version_20220617,
    DataTypeTransactionEvent -> Version_20220617,
    DataTypeDataProviderError -> Version_20220617,
    DataTypeDataProviderCursor -> Version_20220617,
  )

  val dataProviderCovalentHq = new DataProvider(
    source = UUID.fromString("5f4a7bfa-482d-445d-9bda-e83937581026"),
    provider = UUID.fromString("0977c146-f3c5-43c5-a33b-e376eb73ba0b"),
    name = Some("Covalent HQ API"),
  )

  val VersionToDataType = DataTypeToVersion.map(t => t._2 -> t._1)

  object EventType extends Enumeration {
    val approval, transfer, sale, ordersMatched, unknown = Value
  }

  object ContractTypes extends Enumeration {
    val ERC20, ERC721, ERC777, ERC1155 = Value
  }

  object Chain extends Enumeration {
    val ethereum = Value
  }

  object PaymentToken extends Enumeration {
    val eth = Value
  }

  object AddressType extends Enumeration {
    val external, contract = Value
  }

  val PaymentTokenDecimalsMap = Map(
    PaymentToken.eth -> 18,
  )

  val chainPaymentTokenMap = Map(
    Chain.ethereum -> PaymentToken.eth,
  )

}
