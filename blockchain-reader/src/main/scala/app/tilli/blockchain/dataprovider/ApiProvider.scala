package app.tilli.blockchain.dataprovider

import app.tilli.blockchain.codec.BlockchainClasses.DataProviderTrait
import cats.effect.Concurrent
import io.circe.{Decoder, Json}
import org.http4s.EntityDecoder
import org.http4s.client.Client

trait ApiProvider[F[_]] extends DataProviderTrait {

  def concurrent: Concurrent[F]

  implicit def entityDecoderString: EntityDecoder[F, String] = EntityDecoder.text(concurrent)

  implicit def decoderJson: Decoder[Json] = Decoder.decodeJson

  implicit def client: Client[F]

}
