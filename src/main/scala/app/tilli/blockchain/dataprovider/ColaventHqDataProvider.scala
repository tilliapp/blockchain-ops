package app.tilli.blockchain.dataprovider

import app.tilli.api.utils.SimpleHttpClient
import app.tilli.blockchain.codec.BlockchainClasses.{TransactionEventSource, TransactionEventsResult}
import app.tilli.blockchain.codec.BlockchainConfig.{Chain, EventType, PaymentTokenDecimalsMap, chainPaymentTokenMap}
import cats.effect.{Concurrent, Sync}
import io.circe.Json
import io.circe.optics.JsonPath.root
import org.http4s.client.Client
import org.http4s.{Header, Headers}
import org.typelevel.ci.CIString
import upperbound.Limiter

import java.time.Instant
import java.util.UUID
import scala.util.Try

class ColaventHqDataProvider[F[_] : Sync](
  val httpClient: Client[F],
  override val concurrent: Concurrent[F],
) extends ApiProvider[F]
  with TransactionEventSource[F] {

  override val source: UUID = UUID.fromString("5f4a7bfa-482d-445d-9bda-e83937581026")
  override val provider: UUID = UUID.fromString("0977c146-f3c5-43c5-a33b-e376eb73ba0b")
  private val host: String = "https://api.covalenthq.com"
  private val apiKey: String = "ckey_f488176d2b8e42829318059b90e"
  private val headers: Headers = Headers(
    Header.Raw(CIString("X-Api-Key"), apiKey),
    Header.Raw(CIString("Accept"), "application/json"),
  )
  override implicit val client: Client[F] = httpClient

  override def getTransactionEvents(
    address: String,
    chainId: String,
    rateLimiter: Limiter[F],
  ): Either[Throwable, TransactionEventsResult] = {

    val path = s"v1/$chainId/address/$address/transactions_v2/"
    rateLimiter.submit(
      SimpleHttpClient
        .call[F, Json, Json](
          host = host,
          path = path,
          queryParams = Map(
            "key" -> apiKey,
          ),
          headers = headers,
          conversion = json => {

            // getTransactionEventsFromResult()
            Json.fromFields(
              // TODO: Needs unit test. Fails miserably if any of those fields don't exist
              Iterable(
                "address" -> Json.fromString(root.address.string.getOption(json).orNull),
                "openSeaSlug" -> Json.fromString(root.collection.slug.string.getOption(json).orNull),
                "url" -> Json.fromString(root.collection.externalUrl.string.getOption(json).orNull),
                "name" -> Json.fromString(root.collection.name.string.getOption(json).orNull),
                "created" -> Json.fromString(root.createdDate.string.getOption(json).orNull),
                "type" -> Json.fromString(root.assetContractType.string.getOption(json).orNull),
                "schema" -> Json.fromString(root.schemaName.string.getOption(json).orNull),
                "symbol" -> Json.fromString(root.symbol.string.getOption(json).orNull),
                "sourced" -> Json.fromLong(Instant.now().toEpochMilli)
                //                "description" -> Json.fromString(root.description.string.getOption(json).orNull),
              ))
          },
        )
    )
    ???

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

  def getTransactionEventsFromResult(data: Json): List[Json] = {
    root.data.items.each.json.getAll(data).map { eventJson =>

      val chain = Chain.ethereum
      val chainValue = Some(Json.fromString(chain.toString))
      val paymentTokenSymbol = chainPaymentTokenMap.get(chain)
      val paymentTokenSymbolValue = paymentTokenSymbol.map(ps => Json.fromString(ps.toString))
      val paymentTokenDecimals = paymentTokenSymbol.map(ps => Json.fromInt(PaymentTokenDecimalsMap(ps)))

      val transactionHash = root.txHash.string.getOption(eventJson)
      val transactionOffset = root.txOffset.int.getOption(eventJson).map(Json.fromInt)
      val totalPrice = root.value.string.getOption(eventJson).map(Json.fromString)
      val transactionTime = root.blockSignedAt.string.getOption(eventJson)
        .map(ts => if (!ts.toLowerCase.endsWith("z")) s"${ts}Z" else ts)
        .flatMap(ts => Try(Instant.parse(ts)).toOption).map(_.toEpochMilli).map(Json.fromLong)
      val quantity = Some(Json.fromInt(1)) // TODO: Is this a fair assumption? We don't have any quantity counts in covalent

      val logs = root.logEvents.each.json.getAll(eventJson)
        .map { logEvent =>
          val log = root.decoded.name.string.getOption(logEvent) match {
            case Some("Approval") =>
              val eventType = Option(Json.fromString(EventType.approval.toString))
              Json.Null
            case Some("Transfer") =>
              val eventType = Option(Json.fromString(EventType.transfer.toString))
              val logOffset = root.txOffset.int.getOption(logEvent).map(Json.fromInt)
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
                .flatMap(toIntegerFromHexString(_).toOption)
                .map(Json.fromInt)

              val decodedParams = root.decoded.params.arr.getOption(logEvent)
              val from = decodedParams.flatMap(v => v.find(j => root.name.string.getOption(j).contains("from")).flatMap(j => root.value.string.getOption(j))).map(Json.fromString)
              val to = decodedParams.flatMap(v => v.find(j => root.name.string.getOption(j).contains("to")).flatMap(j => root.value.string.getOption(j))).map(Json.fromString)

              Json.fromFields(
                Iterable(
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
            case Some("OrdersMatched") => Json.Null
          }
          log
        }
        .filterNot(_.isNull)

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
          "logs" -> Some(Json.fromValues(logs)),
        ).map(t => t._1 -> t._2.getOrElse(Json.Null))
      )
    }
  }

  def toIntegerFromHexString(hexString: String): Either[Throwable, Int] = {
    Try {
      val hex = if (hexString.startsWith("0x")) hexString.substring(2) else hexString
      Integer.parseInt(hex, 16)
    }.toEither
  }

}
