package app.tilli.api.response

import app.tilli.blockchain.codec.BlockchainClasses.{HttpClientError, TilliHttpCallException}

object Response {

  trait Response

  trait RichResponse extends Response {
    def code: Option[String]

    def message: String
  }

  trait ErrorResponseTrait extends RichResponse

  trait SuccessResponseTrait extends Response

  case class ErrorResponse(
    override val message: String,
    override val code: Option[String],
    detail: Option[String] = None,
  ) extends ErrorResponseTrait

  object ErrorResponse {
    def apply(httpClientError: HttpClientError): ErrorResponse =
      ErrorResponse(
        message = httpClientError.message,
        code = httpClientError.code.map(_.toString),
        detail = httpClientError.url,
      )
  }

  case class HealthCheckSuccess(
    message: String = "healthy",
  ) extends SuccessResponseTrait

}
