package app.tilli.collection

import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.logging.Logging
import cats.MonadThrow
import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import mongo4cats.collection.MongoCollection
import mongo4cats.collection.operations.Filter

class AssetContractCache[F[_] : Sync](
  protected val memoryCache: io.chrisdavenport.mules.Cache[F, String, String],
  protected val collection: MongoCollection[F, TilliAssetContractEvent],
) extends Cache[String, String]
  with CacheBackendMongoDb[F]
  with Logging {

  implicit val F: MonadThrow[F] = MonadThrow[F]

  def lookup(
    assetContract: String,
  ): F[Either[Throwable, Option[String]]] = {
    memoryCache
      .lookup(assetContract)
      .attempt
  }

  def insert(
    k: String,
    v: String,
  ): F[Either[Throwable, Boolean]] = {
    val chain = {
      for {
        bc <- EitherT(Sync[F].pure(Right(false)).asInstanceOf[F[Either[Throwable, Boolean]]])
        mc <- EitherT(memoryCache.insert(k, v).attempt)
      } yield bc
    }
    chain.value
  }

  protected def lookupInBackend(
    k: String,
    collection: MongoCollection[F, TilliAssetContractEvent],
  )(implicit
    F: MonadThrow[F],
  ): F[Either[Throwable, Option[AssetContract]]] = {
    import cats.implicits._
    collection
      .find
      .filter(Filter.eq("key", k))
      .first
      .attempt
      .map(_.map(_.map(_.data)))
  }

}
