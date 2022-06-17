package app.tilli.blockchain.reader

import app.tilli.api.utils.{HttpClientErrorTrait, SimpleHttpClient}
import app.tilli.blockchain.codec.BlockchainClasses.DataProvider
import cats.effect.{Concurrent, Sync}
import io.circe.optics.JsonPath.root
import io.circe.{Decoder, Json, JsonObject}
import org.http4s.client.Client
import org.http4s.{EntityDecoder, Header, Headers}
import org.typelevel.ci.CIString
import upperbound.Limiter

import java.time.Instant
import java.util.UUID

class OpenSeaApi[F[_] : Sync : Concurrent](
  httpClient: Client[F],
  concurrent: Concurrent[F],
) extends DataProvider {

  override def source: UUID = UUID.fromString("7dc94bcb-c490-405b-8989-0efdace798f6")

  override def provider: UUID = UUID.fromString("2365f620-d5b9-43c6-9dd4-986ee8477167")

  private val openseaApiKey: String = "f4104ad1cfc544cdaa7d4e1fb1273fc8"
  private val openseaHost: String = "https://api.opensea.io"
  private val openseaHeaders: Headers = Headers(
    Header.Raw(CIString("X-Api-Key"), openseaApiKey),
    Header.Raw(CIString("Accept"), "application/json"),
  )
  private implicit val entityDecoderString: EntityDecoder[F, String] = EntityDecoder.text(concurrent)
  private implicit val decoderJson: Decoder[Json] = Decoder.decodeJson
  private implicit val client: Client[F] = httpClient

  def getAssetContract(
    trackingId: UUID,
    assetContractAddress: String,
    rateLimiter: Limiter[F],
  ): F[Either[HttpClientErrorTrait, Json]] = {
    val path = s"api/v1/asset_contract/$assetContractAddress"
    rateLimiter.submit(
      SimpleHttpClient
        .call[F, Json, Json](
          host = openseaHost,
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
          headers = openseaHeaders,
        )
    )
  }

  def getEvents(
    trackingId: UUID,
    collectionSlug: String,
    rateLimiter: Limiter[F]
  ): F[Either[HttpClientErrorTrait, Json]] = {
    val path = "api/v1/events"
    val queryParams = Map(
      "collection_slug" -> collectionSlug,
      //      "limit" -> "200",
      "only_opensea" -> "false",
      "event_type" -> "transfer",
    )

    rateLimiter.submit(
      SimpleHttpClient
        .call[F, Json, Json](
          host = openseaHost,
          path = path,
          queryParams = queryParams,
          conversion = json => json,
          headers = openseaHeaders,
        )
    )
  }

}
