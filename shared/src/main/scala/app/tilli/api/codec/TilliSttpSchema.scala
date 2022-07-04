package app.tilli.api.codec

import app.tilli.api.response.Response.ErrorResponse
import app.tilli.blockchain.codec.BlockchainClasses._
import sttp.tapir.Schema

import java.util.UUID

trait TilliSttpSchema {

  implicit lazy val schemaUUID: Schema[UUID] = Schema.schemaForUUID

  implicit lazy val schemaOrigin: Schema[Origin] = Schema.derived
  implicit lazy val schemaHeader: Schema[Header] = Schema.derived

  implicit lazy val errorResponseSchema: Schema[ErrorResponse] = Schema.derived

  implicit lazy val schemaAssetContract: Schema[AssetContract] = Schema.derived
  implicit lazy val schemaAssetContractRequest: Schema[AssetContractRequest] = Schema.derived

}
