package app.tilli.blockchain.codec

import app.tilli.blockchain.codec.BlockchainClasses.DataProvider

object BlockchainConfig {

  val DataTypeAssetContract = "AssetContract"
  val DataTypeAssetContractEventRequest = "AssetContractEventRequest"
  val DataTypeAssetContractEvent = "AssetContractEvent"
  val DataTypeAddressRequest = "AddressRequest"
  val DataTypeTransactionEvent = "TransactionEvent"

  val Version_20220617 = "2022-06-17"
  val DataTypeToVersion = Map(
    DataTypeAssetContract -> Version_20220617,
    DataTypeAssetContractEventRequest -> Version_20220617,
    DataTypeAssetContractEvent -> Version_20220617,
    DataTypeAddressRequest -> Version_20220617,
    DataTypeTransactionEvent -> Version_20220617,
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

  val PaymentTokenDecimalsMap = Map(
    PaymentToken.eth -> 18,
  )

  val chainPaymentTokenMap = Map(
    Chain.ethereum -> PaymentToken.eth,
  )

}
