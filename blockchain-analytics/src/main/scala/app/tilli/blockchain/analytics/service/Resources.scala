package app.tilli.blockchain.analytics.service

import app.tilli.blockchain.analytics.service.config.AppConfig
import app.tilli.blockchain.codec.BlockchainClasses._
import mongo4cats.collection.MongoCollection

case class Resources[F[_]](
  appConfig: AppConfig,
  httpServerPort: Int,
  sslConfig: Option[Map[String, String]],
  transactionCollection: MongoCollection[F, Doc],
)
