package app.tilli.blockchain.codec

import java.util.UUID

object BlockchainClasses {

  trait TilliEvent[A] {
    def header: Header

    def data: A
  }

  case class Origin(
    source: Option[UUID],
    provider: Option[UUID],
    eventTimestamp: Long,
  )

  case class Header(
    trackingId: UUID,
    eventTimestamp: Long,
    eventId: UUID,
    origin: List[Origin],
  )

  case class AssetContractRequest(
    assetContractAddress: Option[String],
    assetContractSlug: Option[String],
    page: Option[String] = None,
  )

  case class AssetContractRequestEvent(
    override val header: Header,
    override val data: AssetContractRequest,
  ) extends TilliEvent[AssetContractRequest]

}
