package app.tilli.persistence.kafka

import cats.effect.{Async, Resource}
import fs2.kafka.{ProducerSettings, RecordSerializer}

trait KafkaProducerTrait[KEY, VALUE] {

  def kafkaProducerConfiguration: KafkaProducerConfiguration

  def withProducerProperties: Map[String, String] = Map.empty

  def producerSettings[F[_]](implicit
    keySerializer: RecordSerializer[F, KEY],
    valueSerializer: RecordSerializer[F, VALUE],
  ): ProducerSettings[F, KEY, VALUE] = ProducerSettings[F, KEY, VALUE]
    .withBootstrapServers(kafkaProducerConfiguration.bootstrapServers)
    .withProperties(withProducerProperties)
    .withRetries(5)

  def producerStream[F[_] : Async](implicit
    keySerializer: RecordSerializer[F, KEY],
    valueSerializer: RecordSerializer[F, VALUE],
  ): fs2.Stream[F, fs2.kafka.KafkaProducer[F, KEY, VALUE]] =
    fs2.kafka.KafkaProducer.stream(producerSettings)

  def producerResource[F[_] : Async](implicit
    keySerializer: RecordSerializer[F, KEY],
    valueSerializer: RecordSerializer[F, VALUE],
  ): Resource[F, fs2.kafka.KafkaProducer.Metrics[F, KEY, VALUE]] =
    fs2.kafka.KafkaProducer.resource(producerSettings)

}
