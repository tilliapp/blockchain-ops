package app.tilli.collection

import cats.data.State
import cats.effect.Ref

class MyLRUCacheMapEffect[F[_], K, V](
  maxEntries: Int,
  initialCapacity: Int = 100,
  loadFactor: Float = 0.75f,
  accessOrder: Boolean = true,
) extends Ref[F, LRUCacheMap[K, V]] {

  private val cache = new LRUCacheMap[K, V](maxEntries, initialCapacity, loadFactor, accessOrder)

  override def access: F[(LRUCacheMap[K, V], LRUCacheMap[K, V] => F[Boolean])] = ???

  override def tryUpdate(f: LRUCacheMap[K, V] => LRUCacheMap[K, V]): F[Boolean] = ???

  override def tryModify[B](f: LRUCacheMap[K, V] => (LRUCacheMap[K, V], B)): F[Option[B]] = ???

  override def update(f: LRUCacheMap[K, V] => LRUCacheMap[K, V]): F[Unit] = ???

  override def modify[B](f: LRUCacheMap[K, V] => (LRUCacheMap[K, V], B)): F[B] = ???

  override def tryModifyState[B](state: State[LRUCacheMap[K, V], B]): F[Option[B]] = ???

  override def modifyState[B](state: State[LRUCacheMap[K, V], B]): F[B] = ???

  override def get: F[LRUCacheMap[K, V]] = ???

  override def set(a: LRUCacheMap[K, V]): F[Unit] = ???
}
