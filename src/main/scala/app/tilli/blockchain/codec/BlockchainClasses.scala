package app.tilli.blockchain.codec

import io.circe.Json

import java.util.UUID

object BlockchainClasses {

  trait TilliEvent[A] {
    def header: Header

    def data: A
  }

  trait Source {
    def source: UUID
  }

  trait Provider {
    def provider: UUID
  }

  trait DataProvider extends Source with Provider {
    def source: UUID

    def provider: UUID
  }

  case class Origin(
    source: Option[UUID],
    provider: Option[UUID],
    sourcedTimestamp: Long,
  )

  case class Header(
    trackingId: UUID,
    eventTimestamp: Long,
    eventId: UUID,
    origin: List[Origin],
    dataType: Option[String],
    version: Option[String],
  )

  case class TilliJsonEvent(
    header: Header,
    data: Json,
  ) extends TilliEvent[Json]

//  case class AssetContractRequest(
//    assetContractAddress: Option[String],
//    page: Option[String] = None,
//  )
//
//  case class AssetContractRequestEvent(
//    override val header: Header,
//    override val data: AssetContractRequest,
//  ) extends TilliEvent[AssetContractRequest]

}
