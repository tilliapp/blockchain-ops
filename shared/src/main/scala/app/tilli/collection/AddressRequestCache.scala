package app.tilli.collection

import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.logging.Logging
import cats.MonadThrow
import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import mongo4cats.collection.operations.{Filter, Sort}
import mongo4cats.collection.{MongoCollection, ReplaceOptions}

import java.time.Instant

class AddressRequestCache[F[_] : Sync](
  protected val memoryCache: io.chrisdavenport.mules.Cache[F, String, AddressRequest],
  protected val collection: MongoCollection[F, AddressRequestRecord],
) extends Cache[String, AddressRequest]
  with CacheBackendMongoDb[F]
  with Logging {

  def recordKey(addressRequest: AddressRequest): String = AddressRequest.keyWithPage(addressRequest)

  implicit val F: MonadThrow[F] = MonadThrow[F]

  def lookup(
    addressRequest: AddressRequest,
    useBackend: Boolean = true,
  ): F[Either[Throwable, Option[AddressRequest]]] = {
    val key = recordKey(addressRequest)
    memoryCache
      .lookup(key)
      .flatMap {
        case Some(ar) =>
          Sync[F].delay(log.info(s"Memory Cache hit: $key")) *>
            Sync[F].pure(Right(Some(ar)))
        case None =>
          Sync[F].delay(log.info(s"Memory Cache miss: $key")) *> {
            if (!useBackend) Sync[F].pure(Right(None))
            else lookupInBackend(key, collection)
              .flatTap(res =>
                if (res.exists(_.nonEmpty)) Sync[F].delay(log.info(s"Mongo Cache hit: $key"))
                else Sync[F].delay(log.info(s"Mongo Cache miss: $key"))
              )
          }
      }
  }

  def insert(
    v: AddressRequest,
    useBackend: Boolean = true,
  ): F[Either[Throwable, Boolean]] =
    insert(recordKey(v), v, useBackend)

  /**
   * @return true if the backend has received it
   */
  def insert(
    k: String,
    v: AddressRequest,
    useBackend: Boolean,
  ): F[Either[Throwable, Boolean]] = {
    val chain = {
      for {
        bc <- EitherT(
          if (!useBackend) Sync[F].pure(Right(false)).asInstanceOf[F[Either[Throwable, Boolean]]]
          else updateInBackend(k, v, collection)
        )
        mc <- EitherT(memoryCache.insert(k, v).attempt)
      } yield bc
    }
    chain.value
  }

  protected def lookupInBackend(
    k: String,
    collection: MongoCollection[F, AddressRequestRecord],
  )(implicit
    F: MonadThrow[F],
  ): F[Either[Throwable, Option[AddressRequest]]] = {
    import cats.implicits._
    collection
      .find
      .sort(Sort.desc("createdAt"))
      .filter(Filter.eq("key", k))
      .first
      .attempt
      .map(_.map(_.map(_.data)))
  }

  protected def updateInBackend(
    k: String,
    v: AddressRequest,
    collection: MongoCollection[F, AddressRequestRecord],
    now: Instant = Instant.now,
  )(implicit
    F: MonadThrow[F],
  ): F[Either[Throwable, Boolean]] = {
    import cats.implicits._
    import mongo4cats.collection.operations._
    val addressRequestRecord = AddressRequestRecord(
      createdAt = now,
      key = k,
      data = v,
    )
    collection
      .replaceOne(
        filter = Filter.eq("key", k)
          .and(Filter.lt("createdAt", now)),
        replacement = addressRequestRecord,
        options = ReplaceOptions().upsert(true),
      )
      .attempt
      .map(_.map(_.wasAcknowledged()))
  }

}
