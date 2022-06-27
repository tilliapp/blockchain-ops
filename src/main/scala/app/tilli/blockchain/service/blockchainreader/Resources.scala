package app.tilli.blockchain.service.blockchainreader

import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.config.AppConfig
import cats.effect.IO
import io.chrisdavenport.mules.Cache
import org.http4s.client.Client
import upperbound.Limiter

case class Resources(
  appConfig: AppConfig,
  sslConfig: Option[Map[String, String]],
  httpClient: Client[IO],
  openSeaRateLimiter: Limiter[IO],
  covalentHqRateLimiter: Limiter[IO],
  etherscanRateLimiter: Limiter[IO],
  assetContractSource: AssetContractSource[IO],
  assetContractEventSource: AssetContractEventSource[IO],
  transactionEventSource: TransactionEventSource[IO],
  assetContractTypeSource: AssetContractTypeSource[IO],
  addressCache: Cache[IO, String, AddressSimple]
)
