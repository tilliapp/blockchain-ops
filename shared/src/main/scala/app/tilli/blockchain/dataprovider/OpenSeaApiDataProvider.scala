package app.tilli.blockchain.dataprovider

import app.tilli.api.utils.SimpleHttpClient
import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.codec.BlockchainConfig.{Chain, EventType, dataProviderOpenSea}
import app.tilli.utils.DateUtils
import cats.data.EitherT
import cats.effect.{Concurrent, Sync}
import io.circe.Json
import io.circe.optics.JsonPath
import io.circe.optics.JsonPath.root
import io.circe.syntax.EncoderOps
import org.http4s.client.Client
import org.http4s.{Header, Headers}
import org.typelevel.ci.CIString
import upperbound.Limiter

import java.time.Instant
import java.util.UUID
import scala.util.Try

class OpenSeaApiDataProvider[F[_] : Sync](
  val httpClient: Client[F],
  override val concurrent: Concurrent[F],
) extends DataProvider(
  dataProviderOpenSea.source,
  dataProviderOpenSea.provider,
  dataProviderOpenSea.name,
  dataProviderOpenSea.defaultPage,
) with ApiProvider[F]
  with AssetContractSource[F]
  with AssetContractEventSource[F] {

  import OpenSeaApiDataProvider._

  private val host: String = "https://api.opensea.io"
  private val apiKey: String = "f4104ad1cfc544cdaa7d4e1fb1273fc8"
  private val headers: Headers = Headers(
    Header.Raw(CIString("X-Api-Key"), apiKey),
    Header.Raw(CIString("Accept"), "application/json"),
  )

  override implicit val client: Client[F] = httpClient

  override def getAssetContract(
    assetContractAddress: String,
    rateLimiter: Option[Limiter[F]],
  ): F[Either[Throwable, Json]] = {
    val path = s"api/v1/asset_contract/$assetContractAddress"
    val httpCall = SimpleHttpClient
      .call[F, Json, Json](
        host = host,
        path = path,
        queryParams = Map.empty,
        conversion = json => decodeAssetContract(json),
        headers = headers,
      )

    rateLimiter.map(_.submit(httpCall)).getOrElse(httpCall)
  }

  override def getAssetContractAddress(tilliJsonEvent: TilliJsonEvent): Either[Throwable, String] =
    root.openSeaCollectionSlug.string.getOption(tilliJsonEvent.data)
      .toRight(new IllegalStateException(s"No slug could be extracted from event $tilliJsonEvent"))

  override def getAssetContractEvents(
    trackingId: UUID,
    assetContractAddress: String,
    nextPage: Option[String],
    rateLimiter: Limiter[F],
  ): F[Either[Throwable, AssetContractEventsResult]] = {
    val path = "api/v1/events"
    val queryParams = Map(
      "collection_slug" -> assetContractAddress,
      "only_opensea" -> "false",
      "event_type" -> "transfer",
      "limit" -> "50",
      //      "occurred_after" -> "",
    ) ++ nextPage.filter(s => s != null && s.nonEmpty).map(np => Map("cursor" -> np)).getOrElse(Map.empty)

    val chain = for {
      result <- EitherT(rateLimiter.submit(
        SimpleHttpClient
          .call[F, Json, Json](
            host = host,
            path = path,
            queryParams = queryParams,
            conversion = json => json,
            headers = headers,
          )
      ))
      events <- EitherT(Sync[F].pure(Right(assetContractEventsFromResult(result)).asInstanceOf[Either[Throwable, List[Json]]]))
      nextPage <- EitherT(Sync[F].pure(Right(getNextPageFromResult(result)).asInstanceOf[Either[Throwable, Option[String]]]))
    } yield AssetContractEventsResult(
      events = events,
      nextPage = nextPage,
    )

    chain.value
  }

}

object OpenSeaApiDataProvider {

  def lense(jsonPath: JsonPath, json: Json): Json =
    jsonPath.string.getOption(json).map(Json.fromString).getOrElse(Json.Null)

  def decodeAssetContract(
    json: Json,
    sourcedTime: Instant = Instant.now,
  ): Json  = {
    Json.fromFields(
      Iterable(
        "address" -> lense(root.address, json),
        "openSeaSlug" -> lense(root.collection.slug, json),
        "url" -> lense(root.collection.externalUrl, json),
        "name" -> lense(root.collection.name, json),
        "created" -> DateUtils.tsToInstant(root.createdDate.string.getOption(json)).map(i => Json.fromString(i.toString)).getOrElse(Json.Null),
        "type" -> lense(root.assetContractType, json),
        "schema" -> lense(root.schemaName, json),
        "symbol" -> lense(root.symbol, json),
        "sourced" ->  Json.fromString(sourcedTime.toString),
      ))
  }

  def getNextPageFromResult(data: Json): Option[String] =
    root.next.string.getOption(data)

  def assetContractEventsFromResult(data: Json): List[Json] = {
    root.assetEvents.each.json.getAll(data).map { eventJson =>

      val chain = Some(Json.fromString(Chain.ethereum.toString))
      val transactionHash = root.transaction.transactionHash.string.getOption(eventJson)
      val eventTypeRaw = root.eventType.string.getOption(eventJson)
      val assetContractAddress = root.asset.assetContract.address.string.getOption(eventJson).map(Json.fromString)
      val assetContractName = root.asset.assetContract.name.string.getOption(eventJson).map(Json.fromString)
      val assetContractSymbol = root.asset.assetContract.symbol.string.getOption(eventJson).map(Json.fromString)
      val tokenType = root.asset.assetContract.schemaName.string.getOption(eventJson).map(Json.fromString)
      val tokenId = root.asset.tokenId.string.getOption(eventJson).map(Json.fromString)
      val quantity = root.quantity.string.getOption(eventJson).flatMap(s => Try(s.toLong).toOption.map(Json.fromLong))
      val transactionTime = DateUtils.tsToInstant(root.eventTimestamp.string.getOption(eventJson)).map(i => Json.fromString(i.toString))
      val paymentTokenSymbol = root.paymentToken.symbol.string.getOption(eventJson).map(Json.fromString)
      val paymentTokenDecimals = root.paymentToken.decimals.int.getOption(eventJson).map(Json.fromInt)
      val totalPrice = root.totalPrice.string.getOption(eventJson).map(Json.fromString)

      val (from, to, eventType) = eventTypeRaw match {
        case Some("successful") =>
          val from = root.seller.address.string.getOption(eventJson).map(Json.fromString)
          val to = root.winnerAccount.address.string.getOption(eventJson).map(Json.fromString)
          (from, to, Some(Json.fromString(EventType.sale.toString)))

        case Some("transfer") =>
          val from = root.fromAccount.address.string.getOption(eventJson).map(Json.fromString)
          val to = root.toAccount.address.string.getOption(eventJson).map(Json.fromString)
          (from, to, Some(Json.fromString(EventType.transfer.toString)))

        case _ => (Some(Json.Null), Some(Json.Null), Some(Json.fromString(EventType.unknown.toString)))
      }
      Json.fromFields(
        Iterable(
          "transactionHash" -> transactionHash.map(Json.fromString),
          "eventType" -> eventType,
          "chain" -> chain,
          "fromAddress" -> from,
          "toAddress" -> to,
          "assetContractAddress" -> assetContractAddress,
          "assetContractName" -> assetContractName,
          "assetContractSymbol" -> assetContractSymbol,
          "tokenType" -> tokenType,
          "tokenId" -> tokenId,
          "quantity" -> quantity,
          "paymentTokenSymbol" -> paymentTokenSymbol,
          "paymentTokenDecimals" -> paymentTokenDecimals,
          "totalPrice" -> totalPrice,
          "transactionTime" -> transactionTime,
        ).map(t => t._1 -> t._2.getOrElse(Json.Null))
      )
    }
  }

}
