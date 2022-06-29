package app.tilli.blockchain.dataprovider

import app.tilli.api.utils.SimpleHttpClient
import app.tilli.blockchain.codec.BlockchainClasses.{DataProvider, DataProviderCursor, TransactionEventSource, TransactionEventsResult}
import app.tilli.blockchain.codec.BlockchainConfig._
import app.tilli.blockchain.dataprovider.ColaventHqDataProvider._
import cats.data.EitherT
import cats.effect.{Concurrent, Sync}
import io.circe.Json
import io.circe.optics.JsonPath.root
import org.http4s.client.Client
import org.http4s.{Header, Headers}
import org.typelevel.ci.CIString
import upperbound.Limiter

import java.math.BigInteger
import java.time.Instant
import scala.util.Try

class ColaventHqDataProvider[F[_] : Sync](
  val httpClient: Client[F],
  override val concurrent: Concurrent[F],
) extends DataProvider(dataProviderCovalentHq.source, dataProviderCovalentHq.provider, dataProviderCovalentHq.name)
  with ApiProvider[F]
  with TransactionEventSource[F] {

  private val host: String = "https://api.covalenthq.com"
  private val apiKey: String = "ckey_f488176d2b8e42829318059b90e"
  private val headers: Headers = Headers(
    Header.Raw(CIString("X-Api-Key"), apiKey),
    Header.Raw(CIString("Accept"), "application/json"),
  )
  override implicit val client: Client[F] = httpClient

  val chainMap = Map(
    Chain.ethereum -> 1,
  )

  override def getTransactionEvents(
    address: String,
    chainId: String,
    page: Option[String],
    rateLimiter: Limiter[F],
  ): F[Either[Throwable, TransactionEventsResult]] = {
    val covalentChain = chainMap(Chain.withName(chainId))
    val path = s"v1/$covalentChain/address/$address/transactions_v2/"
    val pageNumber = page.getOrElse("0")
    val queryParams = Map(
      "key" -> apiKey,
      "page-size" -> "100",
      "block-signed-at-asc" -> "true", // use this to ensure that data is returned chronologically asc
      "page-number" -> pageNumber,
    )
    val chain = for {
      result <- EitherT(rateLimiter.submit(
        SimpleHttpClient
          .call[F, Json, Json](
            host = host,
            path = path,
            queryParams = queryParams,
            headers = headers,
            conversion = json => json,
          )
      ))
    } yield {
      val events = getTransactionEventsFromResult(result, Option(address))
      val nextPage = getNextPageFromResult(result)
      val dataProviderCursor =
        getDataProviderCursor(
          address = address,
          dataProvider = this,
          currentPage = Option(pageNumber),
          query = SimpleHttpClient.toUri(host, path, queryParams).toOption.map(_.renderString),
        )
      TransactionEventsResult(
        events = events,
        nextPage = nextPage,
        dataProviderCursor = dataProviderCursor,
      )
    }
    chain.value
  }
}

object ColaventHqDataProvider {

  def getNextPageFromResult(data: Json): Option[Int] = {
    val hasMore = root.data.pagination.hasMore.boolean.getOption(data).getOrElse(false)
    if (hasMore) {
      val previousPage = root.data.pagination.pageNumber.int.getOption(data).getOrElse(0)
      val nextPage = previousPage + 1
      Some(nextPage)
    } else {
      None
    }
  }

  def getDataProviderCursor(
    address: String,
    dataProvider: DataProvider,
    currentPage: Option[String],
    query: Option[String],
  ): Option[DataProviderCursor] =
    Some(
      DataProviderCursor(
        dataProvider = dataProvider,
        address = address,
        cursor = currentPage,
        query = query,
        createdAt = Option(Instant.now.toEpochMilli),
      )
    )

  def tsToEpochMilli(ts: Option[String]): Option[Long] =
    ts.map(ts => if (!ts.toLowerCase.endsWith("z")) s"${ts}Z" else ts)
      .flatMap(ts => Try(Instant.parse(ts)).toOption).map(_.toEpochMilli)

  def getTransactionEventsFromResult(
    data: Json,
    filterLogsAddress: Option[String] = None,
  ): List[Json] = {
    root.data.items.each.json.getAll(data).flatMap { eventJson =>
      val chain = Chain.ethereum
      val chainValue = Some(Json.fromString(chain.toString))
      val paymentTokenSymbol = chainPaymentTokenMap.get(chain)
      val paymentTokenSymbolValue = paymentTokenSymbol.map(ps => Json.fromString(ps.toString))
      val paymentTokenDecimals = paymentTokenSymbol.map(ps => Json.fromInt(PaymentTokenDecimalsMap(ps)))

      val transactionHash = root.txHash.string.getOption(eventJson)
      val transactionOffset = root.txOffset.int.getOption(eventJson).map(Json.fromInt)
      val totalPrice = root.value.string.getOption(eventJson).map(Json.fromString)
      val quantity = Some(Json.fromInt(1)) // TODO: Is this a fair assumption? We don't have any quantity counts in covalent

      val logs = root.logEvents.each.json.getAll(eventJson)
        .map { logEvent =>
          val log = root.decoded.name.string.getOption(logEvent) match {
            case Some("Transfer") =>
              val decodedParams = root.decoded.params.arr.getOption(logEvent)
              val from = decodedParams.flatMap(v => v.find(j => root.name.string.getOption(j).contains("from")).flatMap(j => root.value.string.getOption(j))).map(Json.fromString)
              val to = decodedParams.flatMap(v => v.find(j => root.name.string.getOption(j).contains("to")).flatMap(j => root.value.string.getOption(j))).map(Json.fromString)

              if (filterLogsAddress.isEmpty || from.flatMap(_.asString).contains(filterLogsAddress.get) || to.flatMap(_.asString).contains(filterLogsAddress.get)) {
                val eventType = Option(Json.fromString(EventType.transfer.toString))
                val transactionTime = tsToEpochMilli(root.blockSignedAt.string.getOption(logEvent)).map(Json.fromLong)
                val blockHeight = root.blockHeight.int.getOption(logEvent).map(Json.fromInt)
                val logOffset = root.logOffset.int.getOption(logEvent).map(Json.fromInt)
                val assetContractAddress = root.senderAddress.string.getOption(logEvent).map(Json.fromString)
                val assetContractName = root.senderName.string.getOption(logEvent).map(Json.fromString)
                val assetContractSymbol = root.senderContractTickerSymbol.string.getOption(logEvent).map(Json.fromString)
                val tokenType = Option(Json.Null)
                val tokenId = root
                  .rawLogTopics
                  .arr
                  .getOption(logEvent)
                  .flatMap(_.lastOption)
                  .flatMap(_.asString)
                  .flatMap(toIntegerStringFromHexString(_).toOption)
                  .map(Json.fromString)

                Json.fromFields(
                  Iterable(
                    "transactionHash" -> transactionHash.map(Json.fromString),
                    "transactionOffset" -> transactionOffset,
                    "chain" -> chainValue,
                    "paymentTokenSymbol" -> paymentTokenSymbolValue,
                    "paymentTokenDecimals" -> paymentTokenDecimals,
                    "totalPrice" -> totalPrice,
                    "quantity" -> quantity,
                    "transactionTime" -> transactionTime,
                    "blockHeight" -> blockHeight,
                    "eventType" -> eventType,
                    "logOffset" -> logOffset,
                    "fromAddress" -> from,
                    "toAddress" -> to,
                    "assetContractAddress" -> assetContractAddress,
                    "assetContractName" -> assetContractName,
                    "assetContractSymbol" -> assetContractSymbol,
                    "tokenType" -> tokenType,
                    "tokenId" -> tokenId,
                  ).map(t => t._1 -> t._2.getOrElse(Json.Null))
                )
              } else {
                Json.Null
              }
            case Some(eventType) =>
              //              //              println(s"Unknown eventType=$eventType")
              Json.Null
            //            case Some("Approval") =>
            //            case Some("OrdersMatched") => Json.Null
            //            case Some("OwnershipTransferred") => Json.Null
            //            case Some("TransferSingle") => Json.Null
            //            case Some("TokensDeposited") => Json.Null
            //            case Some("DepositMade") => Json.Null
            //            case Some("GrantAccepted") => Json.Null
            //            case Some("NameRegistered") => Json.Null
            //            case Some("NewOwner") => Json.Null
            //            case Some("AddrChanged") => Json.Null
            //            case Some("AddressChanged") => Json.Null
            //            case Some("NewResolver") => Json.Null
            //            case Some("Withdrawal") => Json.Null
            //            case Some("ApprovalForAll") => Json.Null
            //            case Some("Upgraded") => Json.Null
            //            case Some("Swap") => Json.Null
            //            case Some("Sync") => Json.Null
            //            case Some("Deposit") => Json.Null
            //            case Some("Loose") => Json.Null
            //            case Some("OrderCancelled") => Json.Null
            //            case Some("Deposited") => Json.Null
            //            case Some("StateSynced") => Json.Null
            //            case Some("Burn") => Json.Null
            //            case Some("UnstakeCompleted") => Json.Null
            //            case Some("JoinQueue") => Json.Null
            //            case Some("DelegateVotesChanged") => Json.Null
            //            case Some("DelegateChanged") => Json.Null
            //            case Some("AuctionBid") => Json.Null
            //            case Some("Redeemed") => Json.Null
            //            case Some("Redeemed") => Json.Null
            case None => Json.Null
          }
          log
        }
        .filterNot(_.isNull)

      if (logs.size > 100)
        println(s"$transactionHash: ${logs.size}")
      if (logs.isEmpty) List.empty
      else logs
    }
  }

  def toIntegerStringFromHexString(hexString: String): Either[Throwable, String] = {
    Try {
      val hex = if (hexString.startsWith("0x")) hexString.substring(2) else hexString
      new BigInteger(hex, 16).toString(10)
    }.toEither
  }

}
