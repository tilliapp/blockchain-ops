package app.tilli.blockchain.codec

import app.tilli.blockchain.codec.BlockchainConfig.{AddressType, DataTypeAnalyticsResultEvent, DataTypeAssetContractRequest, DataTypeToVersion}
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

  trait DataProviderTrait extends Source with Provider {
    def source: UUID

    def provider: UUID

    def name: Option[String] = None

    def defaultPage: String
  }

  class DataProvider(
    override val source: UUID,
    override val provider: UUID,
    override val name: Option[String],
    override val defaultPage: String,
  ) extends DataProviderTrait

  object DataProvider {

    def apply(
      dataProvider: DataProviderTrait,
    ): DataProvider =
      new DataProvider(
        source = dataProvider.source,
        provider = dataProvider.provider,
        name = dataProvider.name,
        defaultPage = dataProvider.defaultPage,
      )

  }

  case class Origin(
    source: Option[UUID],
    provider: Option[UUID],
    sourcedTimestamp: Instant,
  )

  case class Header(
    trackingId: UUID,
    eventTimestamp: Instant,
    eventId: UUID,
    origin: List[Origin],
    dataType: Option[String],
    version: Option[String],
  )

  object Header {

    def apply(
      dataType: String,
      trackingId: Option[UUID] = None,
    ): Header =
      BlockchainClasses.Header(
        trackingId = trackingId.getOrElse(UUID.randomUUID()),
        eventTimestamp = Instant.now,
        eventId = UUID.randomUUID(),
        origin = List.empty,
        dataType = Some(dataType),
        version = DataTypeToVersion.get(dataType),
      )
  }

  case class TilliJsonEvent(
    header: Header,
    data: Json,
  ) extends TilliEvent[Json]

  trait AssetContractSource[F[_]] extends DataProviderTrait {

    def getAssetContract(
      assetContractAddress: String,
      rateLimiter: Option[Limiter[F]],
    ): F[Either[Throwable, Json]]

  }

  trait AssetContractTypeSource[F[_]] extends DataProviderTrait {

    def getAssetContractType(
      assetContractAddress: String,
      rateLimiter: Limiter[F],
    ): F[Either[Throwable, Option[AddressType.Value]]]

  }

  case class AssetContractEventsResult(
    events: List[Json],
    nextPage: Option[String],
  )

  trait AssetContractEventSource[F[_]] extends DataProviderTrait {

    def getAssetContractEvents(
      trackingId: UUID,
      assetContractAddress: String,
      nextPage: Option[String],
      rateLimiter: Limiter[F],
    ): F[Either[Throwable, AssetContractEventsResult]]

    def getAssetContractAddress(tilliJsonEvent: TilliJsonEvent): Either[Throwable, String]
  }

  case class DataProviderCursor(
    dataProvider: DataProvider,
    address: String,
    cursor: Option[String],
    query: Option[String],
    createdAt: Instant = Instant.now,
  )

  object DataProviderCursor {

    def cursorKey(dataProviderCursor: DataProviderCursor): String =
      s"${addressKey(dataProviderCursor.address, dataProviderCursor.dataProvider)}|${dataProviderCursor.cursor.getOrElse(dataProviderCursor.dataProvider.defaultPage)}"

    def addressKey(address: String, dataProvider: DataProvider): String =
      s"$address|${dataProvider.source}|${dataProvider.provider}"

    //     def key(dataProviderCursor: DataProviderCursor): String =
    //      key(
    //        dataProviderCursor.address,
    //        dataProviderCursor.cursor.getOrElse(dataProviderCursor.dataProvider.defaultPage),
    //        dataProviderCursor.dataProvider
    //      )
    //
    //    def key(address: String, page: String, dataProvider: DataProvider): String =
    //      s"$address|${dataProvider.source}|${dataProvider.provider}|$page"

  }

  case class TransactionEventsResult(
    query: Option[String],
    events: List[Json],
    nextPage: Option[Int],
    dataProviderCursor: Option[DataProviderCursor],
  )

  trait TransactionEventSource[F[_]] extends DataProviderTrait {

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
    chain: String,
    dataProvider: DataProvider,
    nextPage: Option[String] = None,
    attempt: Int = 1,
  )

  object AddressRequest {

    def key(addressRequest: AddressRequest): String = {
      //      s"${addressRequest.address}|${addressRequest.chain}|${addressRequest.dataProvider.source}|${addressRequest.dataProvider.provider}|${addressRequest.nextPage.getOrElse(addressRequest.dataProvider.defaultPage)}"
      s"${addressRequest.address}|${addressRequest.chain}|${addressRequest.dataProvider.source}|${addressRequest.dataProvider.provider}"
    }

    def keyWithPage(addressRequest: AddressRequest): String =
      s"${addressRequest.address}|${addressRequest.chain}|${addressRequest.dataProvider.source}|${addressRequest.dataProvider.provider}|${addressRequest.nextPage.getOrElse(addressRequest.dataProvider.defaultPage)}"

  }

  case class AddressRequestRecord(
    key: String,
    data: AddressRequest,
    createdAt: Instant = Instant.now,
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
    key: String,
    data: TransactionRecordData,
    createdAt: Instant = Instant.now,
  )

  case class DataProviderCursorRecord(
    key: String,
    data: DataProviderCursor,
    createdAt: Instant = Instant.now,
  )

  object DataProviderCursorRecord {

    def apply(dataProviderCursor: DataProviderCursor): DataProviderCursorRecord =
      DataProviderCursorRecord(
        key = DataProviderCursor.cursorKey(dataProviderCursor),
        data = dataProviderCursor,
      )
  }

  case class TilliDataProviderError(
    originalEvent: Option[TilliJsonEvent],
    request: Option[String],
    error: Option[String],
    dataSource: Option[UUID],
    dataProvider: Option[UUID],
  )

  trait TilliException extends Throwable

  class TilliHttpCallException(
    val query: Option[String],
    val cause: Throwable,
  ) extends TilliException

  class MaxHttpCallAttemptsReachedException(
    val attempts: Option[Int],
    val cause: TilliHttpCallException,
  ) extends Throwable

  case class AssetContract(
    address: String,
    name: Option[String],
    openSeaSlug: Option[String],
    url: Option[String],
    created: Option[Instant],
    `type`: Option[String],
    schema: Option[String],
    symbol: Option[String],
    sourced: Option[Instant],
  )

  object AssetContract {
    def apply(address: String): AssetContract = AssetContract(
      address = address,
      name = None,
      openSeaSlug = None,
      url = None,
      created = None,
      `type` = None,
      schema = None,
      symbol = None,
      sourced = None,
    )
  }

  case class TilliAssetContractRequestEvent(
    override val header: Header,
    override val data: AssetContractRequest
  ) extends TilliEvent[AssetContractRequest]

  object TilliAssetContractRequestEvent {

    def apply(assetContractRequest: AssetContractRequest): TilliAssetContractRequestEvent = {
      TilliAssetContractRequestEvent(
        header = Header(
          trackingId = UUID.randomUUID(),
          eventTimestamp = Instant.now(),
          eventId = UUID.randomUUID(),
          origin = List.empty,
          dataType = Some(DataTypeAssetContractRequest),
          version = DataTypeToVersion.get(DataTypeAssetContractRequest)
        ),
        data = assetContractRequest,
      )
    }

  }

  case class TilliAssetContractEvent(
    override val header: Header,
    override val data: AssetContract
  ) extends TilliEvent[AssetContract]

  case class AssetContractRequest(
    assetContract: AssetContract,
    attempt: Option[Int] = Some(1),
    startSync: Option[Boolean] = Some(false),
  )

  case class SimpleTransaction(
    transactionHash: Option[String],
    toAddress: Option[String],
    fromAddress: Option[String],
    tokenId: Option[String],
    assetContractAddress: Option[String],
    assetContractName: Option[String],
    assetContractType: Option[String],
    transactionTime: Option[Instant],
    totalPrice: Option[Long],
  )

  case class Doc(
    data: SimpleTransaction,
    assetContractType: Option[String],
  )

  case class AnalyticsRequest(
    address: String,
    assetContractAddress: Option[String],
    tokenId: Option[String],
  )

  case class TilliAnalyticsAddressRequestEvent(
    header: Header,
    data: AnalyticsRequest,
  ) extends TilliEvent[AnalyticsRequest]

  case class AnalyticsResult(
    address: String,
    tokenId: String,
    assetContractAddress: String,
    assetContractName: Option[String],
    assetContractType: Option[String],
    count: Option[Int],
    duration: Option[Long],
    originatedFromNullAddress: Boolean,
    transactions: Option[List[String]],
  )

  case class AnalyticsResultStatsV1(
    address: String,
    holdTimeAvg: Option[Double],
    holdTimeMax: Option[Int],
    holdTimeMin: Option[Int],
    mints: Option[Int],
    transactions:Option[Int],
    tokens: Option[Int],
    assetContracts: Iterable[String],
    assetContractCount: Option[Int],
  )

  case class TilliAnalyticsResultEvent(
    header: Header,
    data: AnalyticsResult,
  ) extends TilliEvent[AnalyticsResult]


  case class TilliAnalyticsResultStatsV1Event(
    header: Header,
    data: AnalyticsResultStatsV1,
  ) extends TilliEvent[AnalyticsResultStatsV1]
}
