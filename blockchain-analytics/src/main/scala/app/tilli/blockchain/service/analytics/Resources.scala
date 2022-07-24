package app.tilli.blockchain.service.analytics

import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.service.analytics.config.AppConfig
import app.tilli.collection.{AddressRequestCache, AssetContractCache}
import cats.effect.IO
import mongo4cats.collection.MongoCollection

case class Resources[F[_]](
  appConfig: AppConfig,
  httpServerPort: Int,
  sslConfig: Option[Map[String, String]],
  transactionCollection: MongoCollection[F, Doc],
  assetContractCache: AssetContractCache[IO],
)
