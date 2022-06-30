package app.tilli.collection

import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.codec.BlockchainCodec._
import cats.MonadThrow
import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import io.circe.Json
import io.circe.optics.JsonPath.root
import io.circe.syntax.EncoderOps
import mongo4cats.collection.{MongoCollection, UpdateOptions}
import mongo4cats.collection.operations.{Filter, Sort}

import java.time.Instant

class AddressRequestCache[F[_] : Sync](
  protected val memoryCache: io.chrisdavenport.mules.Cache[F, String, AddressRequest],
  protected val collection: MongoCollection[F, Json],
) extends Cache[String, AddressRequest]
  with CacheBackendMongoDb[F] {

  def recordKey(addressRequest: AddressRequest): String = AddressRequest.key(addressRequest)

  implicit val F: MonadThrow[F] = MonadThrow[F]

  def lookup(
    addressRequest: AddressRequest,
  ): F[Either[Throwable, Option[AddressRequest]]] = {
    val key = recordKey(addressRequest)
    memoryCache
      .lookup(key)
      .flatMap {
        case Some(ar) => Sync[F].pure(Right(Some(ar)))
        case None => lookupInBackend(key, collection)
      }
  }

  def insert(v: AddressRequest): F[Either[Throwable, Boolean]] =
    insert(recordKey(v), v)

  /**
   * @return true if the backend has received it
   */
  def insert(
    k: String,
    v: AddressRequest,
  ): F[Either[Throwable, Boolean]] = {
    val chain = {
      for {
        bc <- EitherT(updateInBackend(k, v, collection))
        _ <- EitherT(memoryCache.insert(k, v).attempt)
      } yield bc
    }
    chain.value
  }

  protected def lookupInBackend(
    k: String,
    collection: MongoCollection[F, Json],
  )(implicit
    F: MonadThrow[F],
  ): F[Either[Throwable, Option[AddressRequest]]] = {
    import cats.implicits._
    collection
      .find
      .sort(Sort.desc("data.data.createdAt"))
      .filter(Filter.eq("data.key", k))
      .first
      .attempt
      .map(_.map(_.flatMap(j => root.data.json.getOption(j).map(_.as[AddressRequestRecord]))))
      .flatMap {
        case Right(result) =>
          F.pure(
            result
              .map(_.leftMap(err => new IllegalStateException(s"Could not deserialize AddressRequestRecord for key $k", err)))
              .sequence.asInstanceOf[Either[Throwable, Option[AddressRequest]]]
          )
        case Left(err) => F.pure(Left(err))
      }
  }

  protected def updateInBackend(
    k: String,
    v: AddressRequest,
    collection: MongoCollection[F, Json],
    now: Instant = Instant.now,
  )(implicit
    F: MonadThrow[F],
  ): F[Either[Throwable, Boolean]] = {
    import cats.implicits._
    import mongo4cats.collection.operations._
    import mongo4cats.circe._
    collection
      .updateOne(
        filter = Filter.eq("data.key", k)
          .and(Filter.lt("data.createdAt", now.toEpochMilli)),
        update = Update.set("data", v.asJson),
        options = UpdateOptions().upsert(true),
      )
      .attempt
      .map(_.map(_.wasAcknowledged()))
  }

}
