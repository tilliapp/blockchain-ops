package app.tilli.blockchain.service.mongodbsink

import app.tilli.blockchain.config.AppConfig
import io.circe.Json
import mongo4cats.client.MongoClient
import mongo4cats.collection.MongoCollection
import mongo4cats.database.MongoDatabase
import org.http4s.client.Client

case class Resources[F[_]](
  appConfig: AppConfig,
  sslConfig: Option[Map[String, String]],
  httpClient: Client[F],
  mongoClient: MongoClient[F],
  mongoDatabase: MongoDatabase[F],
  transactionCollection: MongoCollection[F, Json],
)