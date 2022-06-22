package app.tilli.collection

import cats.effect.kernel.{Async, Resource}
import io.chrisdavenport.mules.{MemoryCache, TimeSpec}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object MemCache {

  def resource[F[_]: Async, K, V](
    duration: FiniteDuration = 1.second,
  ): Resource[F, MemoryCache[F, K, V]] = {
    Resource.eval(create[F, K, V](duration))
  }

  def create[F[_]: Async, K, V](
    duration: FiniteDuration = 1.second,
  ): F[MemoryCache[F, K, V]] = {
    MemoryCache.ofSingleImmutableMap[F, K, V](
      defaultExpiration = Some(TimeSpec.unsafeFromDuration(duration))
    )(Async[F])
  }

}
