package app.tilli.blockchain.codec

import app.tilli.serializer.Fs2KafkaCodec.jsonDeserializer
import cats.effect.IO
import fs2.kafka.Deserializer
import io.circe.Json

object BlockchainCodec {

  implicit val deserializerJson: Deserializer[IO, Json] = jsonDeserializer

}
