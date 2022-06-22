package app.tilli.blockchain.codec

import app.tilli.api.utils.HttpClientErrorTrait
import app.tilli.blockchain.codec.BlockchainConfig.AddressType
import io.circe.Json
import upperbound.Limiter

import java.time.Instant
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
      assetContractAddress: String,
      rateLimiter: Limiter[F],
    ): F[Either[HttpClientErrorTrait, Json]]

  }

  trait AssetContractTypeSource[F[_]] extends DataProvider {

    def getAssetContractType(
      assetContractAddress: String,
      rateLimiter: Limiter[F],
    ): F[Either[HttpClientErrorTrait, Option[AddressType.Value]]]

  }

  case class AssetContractEventsResult(
    events: List[Json],
    nextPage: Option[String],
  )

  trait AssetContractEventSource[F[_]] extends DataProvider {

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
    nextPage: Option[Int],
  )

  trait TransactionEventSource[F[_]] extends DataProvider {

    def getTransactionEvents(
      address: String,
      chainId: String,
      nextPage: Option[String],
      rateLimiter: Limiter[F],
    ): F[Either[HttpClientErrorTrait, TransactionEventsResult]]

  }

  case class AssetContractHolderRequest(
    assetContractAddress: Option[String],
    openSeaCollectionSlug: Option[String],
    nextPage: Option[String],
    attempt: Int = 1,
  )

  case class AddressRequest(
    address: String,
    chain: Option[String],
    nextPage: Option[String] = None,
    attempt: Int = 1,
  )

  case class AddressSimple(
    address: String,
    isContract: Option[Boolean],
    created: Instant = Instant.now(),
  )
}
