package app.tilli.blockchain.codec

import app.tilli.blockchain.codec.BlockchainClasses.DataProvider

object BlockchainConfig {

  val DataTypeAssetContract = "AssetContract"

  val DataTypeToVersion = Map(
    DataTypeAssetContract -> "2022-06-17"
  )

  val VersionToDataType = DataTypeToVersion.map(t => t._2 -> t._1)

}
