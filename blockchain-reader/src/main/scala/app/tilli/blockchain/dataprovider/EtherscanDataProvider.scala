package app.tilli.blockchain.dataprovider

import app.tilli.api.utils.SimpleHttpClient
import app.tilli.blockchain.codec.BlockchainClasses.{AssetContractTypeSource, HttpClientErrorTrait}
import app.tilli.blockchain.codec.BlockchainConfig.AddressType
import cats.effect.Concurrent
import cats.effect.kernel.Sync
import io.circe.Json
import io.circe.optics.JsonPath.root
import org.http4s.client.Client
import upperbound.Limiter

import java.util.UUID

class EtherscanDataProvider[F[_] : Sync](
  val httpClient: Client[F],
  override val concurrent: Concurrent[F],
) extends ApiProvider[F]
  with AssetContractTypeSource[F] {
  override val source: UUID = UUID.fromString("d230ad58-7748-4369-ab9c-e3e11295b6f5")
  override val provider: UUID = UUID.fromString("5edcb2aa-8f87-4f90-a5f5-531220eff058")
  override implicit val client: Client[F] = httpClient

  val etherScanHost = "https://api.etherscan.io"
  val etherScanApiKey = "2F4I4U42A674STIFNB4M522BRFSP8MHQHA"

  override def getAssetContractType(
    assetContractAddress: String,
    rateLimiter: Limiter[F],
  ): F[Either[HttpClientErrorTrait, Option[AddressType.Value]]] = {
    val path = "api"
    val queryParams = Map(
      "module" -> "contract",
      "action" -> "getabi",
      "address" -> assetContractAddress,
      "apikey" -> etherScanApiKey,
    )

    rateLimiter.submit(
      SimpleHttpClient
        .call[F, Json, Option[AddressType.Value]](
          host = etherScanHost,
          path = path,
          queryParams = queryParams,
          conversion = json =>
            root.status.string.getOption(json)
              .map(s => if (s == "1") AddressType.contract else AddressType.external)
        )
    )
  }

}
