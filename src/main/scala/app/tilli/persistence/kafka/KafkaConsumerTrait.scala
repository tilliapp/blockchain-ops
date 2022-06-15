package app.tilli.persistence.kafka

import cats.effect.Async
import cats.effect.kernel.Resource
import fs2.kafka.{ConsumerSettings, IsolationLevel, RecordDeserializer}

trait KafkaConsumerTrait[KEY, VALUE] {

  def kafkaConsumerConfiguration: KafkaConsumerConfiguration

  def withConsumerProperties: Map[String, String] = Map.empty

  def consumerSettings[F[_]](implicit
    keyDeserializer: RecordDeserializer[F, KEY],
    valueDeserializer: RecordDeserializer[F, VALUE],
  ): ConsumerSettings[F, KEY, VALUE] = ConsumerSettings[F, KEY, VALUE]
    .withBootstrapServers(kafkaConsumerConfiguration.bootstrapServers)
    .withGroupId(kafkaConsumerConfiguration.groupId)
    .withProperties(withConsumerProperties)
    .withIsolationLevel(IsolationLevel.ReadCommitted)

  def consumerStream[F[_] : Async](implicit
    keyDeserializer: RecordDeserializer[F, KEY],
    valueDeserializer: RecordDeserializer[F, VALUE],
  ): fs2.Stream[F, fs2.kafka.KafkaConsumer[F, KEY, VALUE]] =
    fs2.kafka.KafkaConsumer.stream(consumerSettings)

  def consumerResource[F[_] : Async](implicit
    keyDeserializer: RecordDeserializer[F, KEY],
    valueDeserializer: RecordDeserializer[F, VALUE],
  ): Resource[F, fs2.kafka.KafkaConsumer[F, KEY, VALUE]] =
    fs2.kafka.KafkaConsumer.resource(consumerSettings)

}
