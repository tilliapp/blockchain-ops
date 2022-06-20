package app.tilli.blockchain.codec

import app.tilli.blockchain.codec.BlockchainClasses.DataProvider

object BlockchainConfig {

  val DataTypeAssetContract = "AssetContract"
  val DataTypeAssetContractEventRequest = "AssetContractEventRequest"
  val DataTypeAssetContractEvent = "AssetContractEvent"

  val Version_20220617 = "2022-06-17"
  val DataTypeToVersion = Map(
    DataTypeAssetContract -> Version_20220617,
    DataTypeAssetContractEvent -> Version_20220617,
    DataTypeAssetContractEventRequest -> Version_20220617,
  )

  val VersionToDataType = DataTypeToVersion.map(t => t._2 -> t._1)

  object EventType extends Enumeration {
    val transfer, sale, unknown = Value
  }

  object ContractTypes extends Enumeration {
    val ERC20, ERC721, ERC777, ERC1155 = Value
  }

}
