package app.tilli.blockchain.service

import app.tilli.blockchain.codec.BlockchainClasses.{AssetContractEventSource, AssetContractSource, TransactionEventSource}
import app.tilli.blockchain.config.AppConfig
import cats.effect.IO
import org.http4s.client.Client
import upperbound.Limiter

case class Resources(
  appConfig: AppConfig,
  httpClient: Client[IO],
  openSeaRateLimiter: Limiter[IO],
  assetContractSource: AssetContractSource[IO],
  assetContractEventSource: AssetContractEventSource[IO],
  transactionEventSource: TransactionEventSource[IO],
)