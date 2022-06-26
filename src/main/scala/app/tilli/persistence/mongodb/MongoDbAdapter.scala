package app.tilli.persistence.mongodb

import cats.effect.{Async, Resource}
import mongo4cats.client.MongoClient

object MongoDbAdapter {

  def resource[F[_] : Async](
    url: String,
  ): Resource[F, MongoClient[F]] =
    MongoClient.fromConnectionString[F](url)

}