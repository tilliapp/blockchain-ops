package app.tilli.blockchain.dataprovider

import app.tilli.api.utils.SimpleHttpClient
import app.tilli.blockchain.codec.BlockchainClasses
import app.tilli.blockchain.codec.BlockchainClasses.{DataProvider, TransactionEventSource, TransactionEventsResult}
import cats.effect.{Concurrent, Sync}
import io.circe.Json
import io.circe.optics.JsonPath.root
import org.http4s.{Header, Headers}
import org.http4s.client.Client
import org.typelevel.ci.CIString
import upperbound.Limiter

import java.time.Instant
import java.util.UUID

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
    ???
  }


}
