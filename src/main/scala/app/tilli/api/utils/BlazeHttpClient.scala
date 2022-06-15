package app.tilli.api.utils

import cats.effect.Resource
import cats.effect.kernel.Async
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import org.http4s.client.middleware.{Retry, RetryPolicy}

import scala.concurrent.duration.{Duration, _}
import scala.language.higherKinds

/**
 * Http Client resource based on Blaze.
 *
 * Maybe consider EmberClientBuilder?
 */
object BlazeHttpClient {

  def clientWithRetry[F[_] : Async](
    httpClientConfig: HttpClientConfig,
  ): Resource[F, Client[F]] =
    clientWithRetry(
      httpClientConfig.connectTimeoutSecs.seconds,
      httpClientConfig.requestTimeoutSecs.seconds,
      httpClientConfig.maxRetryWaitMilliSecs.milliseconds,
      httpClientConfig.maxRetries,
    )

  def clientWithRetry[F[_] : Async](
    connectTimeout: Duration,
    requestTimeout: Duration,
    maxRetryWait: Duration,
    maxRetries: Int,
  ): Resource[F, Client[F]] =
    client(connectTimeout, requestTimeout)
      .map(withRetry(_, maxRetryWait, maxRetries))

  def client[F[_] : Async](
    connectTimeout: Duration,
    requestTimeout: Duration,
  ): Resource[F, Client[F]] =
    BlazeClientBuilder[F]
      .withConnectTimeout(connectTimeout)
      .withRequestTimeout(requestTimeout)
      .resource

  def withRetry[F[_] : Async](
    client: Client[F],
    maxRetryWait: Duration,
    maxRetries: Int,
  ): Client[F] =
    Retry(policy = RetryPolicy[F](backoff = RetryPolicy.exponentialBackoff(maxRetryWait, maxRetries)))(client)
}
