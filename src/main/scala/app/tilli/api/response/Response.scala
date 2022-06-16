package app.tilli.api.response

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
  ) extends ErrorResponseTrait

  case class HealthCheckSuccess(
    message: String = "healthy",
  ) extends SuccessResponseTrait

}
