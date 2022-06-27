package app.tilli.blockchain.service.blockchainsink

import app.tilli.blockchain.codec.BlockchainClasses.TransactionRecord
import app.tilli.blockchain.service.blockchainsink.config.AppConfig
import mongo4cats.client.MongoClient
import mongo4cats.collection.MongoCollection
import mongo4cats.database.MongoDatabase

case class Resources[F[_]](
  appConfig: AppConfig,
  sslConfig: Option[Map[String, String]],
  mongoClient: MongoClient[F],
  mongoDatabase: MongoDatabase[F],
  transactionCollection: MongoCollection[F, TransactionRecord],
)