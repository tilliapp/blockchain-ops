package app.tilli.blockchain.analytics.service

import cats.effect.Async

object NftHolding {

  def load[F[_] : Async](
    r: Resources,
  ): F[Unit] = ???

}
