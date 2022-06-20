package app.tilli.blockchain.codec

import app.tilli.api.utils.HttpClientErrorTrait
import io.circe.Json
import upperbound.Limiter

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

  trait AssetContractSource[F[_]] extends DataProvider {

    def getAssetContract(
      trackingId: UUID,
      assetContractAddress: String,
      rateLimiter: Limiter[F],
    ): F[Either[HttpClientErrorTrait, Json]]

  }

  case class AssetContractEventsResult(
    events: List[Json],
    nextPage: Option[String],
  )

  trait AssetContractEventSource[F[_]] {

    def getAssetContractEvents(
      trackingId: UUID,
      assetContractAddress: String,
      nextPage: Option[String],
      rateLimiter: Limiter[F],
    ): F[Either[HttpClientErrorTrait, AssetContractEventsResult]]

    def getAssetContractAddress(tilliJsonEvent: TilliJsonEvent): Either[Throwable, String]
  }

  case class TransactionEventsResult(
    events: List[Json],
    nextPage: Option[String],
  )


  trait TransactionEventSource[F[_]] {

    def getTransactionEvents(
      address: String,
      chainId: String,
      rateLimiter: Limiter[F],
    ): Either[Throwable, TransactionEventsResult]

  }

  case class AssetContractHolderRequest(
    assetContractAddress: Option[String],
    openSeaCollectionSlug: Option[String],
    nextPage: Option[String],
    attempt: Int = 1,
  )
}
