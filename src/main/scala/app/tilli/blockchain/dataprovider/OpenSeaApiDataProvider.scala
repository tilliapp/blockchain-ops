package app.tilli.blockchain.dataprovider

import app.tilli.api.utils.SimpleHttpClient
import app.tilli.blockchain.codec.BlockchainClasses.{AssetContractEventSource, AssetContractEventsResult, AssetContractSource, HttpClientErrorTrait, TilliJsonEvent}
import app.tilli.blockchain.codec.BlockchainConfig.{Chain, EventType}
import cats.data.EitherT
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

class OpenSeaApi[F[_] : Sync](
  val httpClient: Client[F],
  override val concurrent: Concurrent[F],
) extends ApiProvider[F]
  with AssetContractSource[F]
  with AssetContractEventSource[F] {

  import OpenSeaApi._

  override val source: UUID = UUID.fromString("7dc94bcb-c490-405b-8989-0efdace798f6")
  override val provider: UUID = UUID.fromString("2365f620-d5b9-43c6-9dd4-986ee8477167")

  private val host: String = "https://api.opensea.io"
  private val apiKey: String = "f4104ad1cfc544cdaa7d4e1fb1273fc8"
  private val headers: Headers = Headers(
    Header.Raw(CIString("X-Api-Key"), apiKey),
    Header.Raw(CIString("Accept"), "application/json"),
  )

//  private implicit val entityDecoderString: EntityDecoder[F, String] = EntityDecoder.text(concurrent)
//  private implicit val decoderJson: Decoder[Json] = Decoder.decodeJson
  override implicit val client: Client[F] = httpClient

  override def getAssetContract(
    assetContractAddress: String,
    rateLimiter: Limiter[F],
  ): F[Either[HttpClientErrorTrait, Json]] = {
    val path = s"api/v1/asset_contract/$assetContractAddress"
    rateLimiter.submit(
      SimpleHttpClient
        .call[F, Json, Json](
          host = host,
          path = path,
          queryParams = Map.empty,
          conversion = json => {
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
          headers = headers,
        )
    )
  }

  override def getAssetContractAddress(tilliJsonEvent: TilliJsonEvent): Either[Throwable, String] =
    root.openSeaCollectionSlug.string.getOption(tilliJsonEvent.data)
      .toRight(new IllegalStateException(s"No slug could be extracted from event $tilliJsonEvent"))

  override def getAssetContractEvents(
    trackingId: UUID,
    assetContractAddress: String,
    nextPage: Option[String],
    rateLimiter: Limiter[F],
  ): F[Either[HttpClientErrorTrait, AssetContractEventsResult]] = {
    val path = "api/v1/events"
    val queryParams = Map(
      "collection_slug" -> assetContractAddress,
      "only_opensea" -> "false",
      "event_type" -> "transfer",
      "limit" -> "50",
      //      "occurred_after" -> "",
    ) ++ nextPage.map(np => Map("cursor" -> np)).getOrElse(Map.empty)

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
      events <- EitherT(Sync[F].pure(Right(assetContractEventsFromResult(result)).asInstanceOf[Either[HttpClientErrorTrait, List[Json]]]))
      nextPage <- EitherT(Sync[F].pure(Right(getNextPageFromResult(result)).asInstanceOf[Either[HttpClientErrorTrait, Option[String]]]))
    } yield AssetContractEventsResult(
      events = events,
      nextPage = nextPage,
    )

    chain.value
  }

}

object OpenSeaApi {

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
      val transactionTime = root.eventTimestamp.string.getOption(eventJson)
        .map(ts => if (!ts.toLowerCase.endsWith("z")) s"${ts}Z" else ts)
        .flatMap(ts => Try(Instant.parse(ts)).toOption).map(_.toEpochMilli).map(Json.fromLong)
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
