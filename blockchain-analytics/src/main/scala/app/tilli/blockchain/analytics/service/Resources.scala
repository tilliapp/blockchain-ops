package app.tilli.blockchain.analytics.service

import app.tilli.blockchain.analytics.service.config.AppConfig
import app.tilli.blockchain.codec.BlockchainClasses._
import cats.effect.IO
import mongo4cats.collection.MongoCollection

case class Resources(
  appConfig: AppConfig,
  httpServerPort: Int,
  transactionCollection: MongoCollection[IO, Doc],
)
