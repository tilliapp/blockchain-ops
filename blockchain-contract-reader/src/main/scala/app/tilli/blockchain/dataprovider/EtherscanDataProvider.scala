//package app.tilli.blockchain.dataprovider
//
//import app.tilli.api.utils.SimpleHttpClient
//import app.tilli.blockchain.codec.BlockchainClasses.{AssetContractTypeSource, DataProvider}
//import app.tilli.blockchain.codec.BlockchainConfig.{AddressType, dataProviderEtherscan}
//import cats.effect.Concurrent
//import cats.effect.kernel.Sync
//import io.circe.Json
//import io.circe.optics.JsonPath.root
//import org.http4s.client.Client
//import upperbound.Limiter
//
//class EtherscanDataProvider[F[_] : Sync](
//  val httpClient: Client[F],
//  override val concurrent: Concurrent[F],
//) extends DataProvider(
//  dataProviderEtherscan.source,
//  dataProviderEtherscan.provider,
//  dataProviderEtherscan.name,
//  dataProviderEtherscan.defaultPage,
//) with ApiProvider[F]
//  with AssetContractTypeSource[F] {
//  override implicit val client: Client[F] = httpClient
//
//  val etherScanHost = "https://api.etherscan.io"
//  val etherScanApiKey = "2F4I4U42A674STIFNB4M522BRFSP8MHQHA"
//
//  override def getAssetContractType(
//    assetContractAddress: String,
//    rateLimiter: Limiter[F],
//  ): F[Either[Throwable, Option[AddressType.Value]]] = {
//    val path = "api"
//    val queryParams = Map(
//      "module" -> "contract",
//      "action" -> "getabi",
//      "address" -> assetContractAddress,
//      "apikey" -> etherScanApiKey,
//    )
//
//    rateLimiter.submit(
//      SimpleHttpClient
//        .call[F, Json, Option[AddressType.Value]](
//          host = etherScanHost,
//          path = path,
//          queryParams = queryParams,
//          conversion = json =>
//            root.status.string.getOption(json)
//              .map(s => if (s == "1") AddressType.contract else AddressType.external)
//        )
//    )
//  }
//
//}
