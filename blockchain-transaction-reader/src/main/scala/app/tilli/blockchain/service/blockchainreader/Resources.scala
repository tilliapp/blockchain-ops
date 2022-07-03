package app.tilli.blockchain.service.blockchainreader

import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.service.blockchainreader.config.AppConfig
import app.tilli.collection.AddressRequestCache
import cats.effect.IO
import io.chrisdavenport.mules.Cache
import io.circe.Json
import mongo4cats.collection.MongoCollection
import org.http4s.client.Client
import upperbound.Limiter

case class Resources(
  appConfig: AppConfig,
  httpServerPort: Int,
  sslConfig: Option[Map[String, String]],
  httpClient: Client[IO],
  covalentHqRateLimiter: Limiter[IO],
  transactionEventSource: TransactionEventSource[IO],
  addressRequestCacheTransactions: AddressRequestCache[IO],
  dataProviderCursorCache: Cache[IO, String, DataProviderCursor],
  dataProviderCursorCollection: MongoCollection[IO, DataProviderCursorRecord],
)
