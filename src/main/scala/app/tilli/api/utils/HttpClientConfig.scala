package app.tilli.api.utils

case class HttpClientConfig(
  connectTimeoutSecs: Int,
  requestTimeoutSecs: Int,
  maxRetryWaitMilliSecs: Int,
  maxRetries: Int,
)