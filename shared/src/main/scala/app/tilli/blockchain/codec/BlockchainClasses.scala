package app.tilli.blockchain.codec

import app.tilli.blockchain.codec.BlockchainConfig.AddressType
import io.circe.Json
import upperbound.Limiter

import java.time.Instant
import java.util.UUID

object BlockchainClasses {

  trait HttpClientErrorTrait extends Throwable {
    def message: String

    def detail: Option[String]

    def code: Option[Int]

    def reason: Option[String]

    def headers: Option[String]

    def url: Option[String]
  }

  case class HttpClientError(
    override val message: String,
    override val detail: Option[String],
    override val code: Option[Int],
    override val reason: Option[String],
    override val headers: Option[String],
    override val url: Option[String],
  ) extends HttpClientErrorTrait

  object HttpClientError {

    def apply(e: Throwable): HttpClientError =
      new HttpClientError(
        message = e.getMessage,
        detail = None,
        code = None,
        reason = Option(e.getCause).filter(_ != null).map(_.getMessage),
        headers = None,
        url = None,
      )

  }

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
    ): F[Either[Throwable, Json]]

  }

  trait AssetContractTypeSource[F[_]] extends DataProvider {

    def getAssetContractType(
      assetContractAddress: String,
      rateLimiter: Limiter[F],
    ): F[Either[Throwable, Option[AddressType.Value]]]

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
    ): F[Either[Throwable, AssetContractEventsResult]]

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
    ): F[Either[Throwable, TransactionEventsResult]]

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

  case class TransactionRecordData(
    transactionHash: Option[String],
    transactionOffset: Option[Long],
    chain: Option[String],
    paymentTokenSymbol: Option[String],
    paymentTokenDecimals: Option[Int],
    totalPrice: Option[String],
    quantity: Option[Long],
    transactionTime: Option[Instant],
    eventType: Option[String],
    logOffset: Option[Long],
    fromAddress: Option[String],
    toAddress: Option[String],
    assetContractAddress: Option[String],
    assetContractName: Option[String],
    assetContractSymbol: Option[String],
    tokenType: Option[String],
    tokenId: Option[String],
    createdAt: Option[Instant] = Some(Instant.now),
  )

  case class TransactionRecord(
    transactionTime: Option[Instant],
    key: Option[String],
    data: TransactionRecordData,
  )

  case class TilliDataProviderError(
    originalEvent: Option[TilliJsonEvent],
    request: Option[String],
    error: Option[String],
    dataSource: Option[UUID],
    dataProvider: Option[UUID],
  )
}
