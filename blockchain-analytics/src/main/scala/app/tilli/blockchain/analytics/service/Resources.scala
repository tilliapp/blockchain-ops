package app.tilli.blockchain.analytics.service

import app.tilli.blockchain.analytics.service.config.AppConfig
import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.collection.{AddressRequestCache, AssetContractCache}
import cats.effect.IO
import io.chrisdavenport.mules.Cache
import mongo4cats.collection.MongoCollection
import org.http4s.client.Client
import upperbound.Limiter

case class Resources(
  appConfig: AppConfig,
  httpServerPort: Int,
  transactionCollection: MongoCollection[IO, TransactionRecord],
)
