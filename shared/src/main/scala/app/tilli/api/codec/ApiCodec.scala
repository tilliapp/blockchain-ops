package app.tilli.api.codec

import app.tilli.api.response.Response._
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

trait ApiCodec {

  implicit lazy val codecErrorResponse: Codec[ErrorResponse] = deriveCodec
  implicit lazy val codecHealthCheckSuccess: Codec[HealthCheckSuccess] = deriveCodec
}

object ApiCodec extends ApiCodec
