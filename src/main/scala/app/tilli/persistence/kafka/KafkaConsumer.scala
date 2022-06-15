package app.tilli.persistence.kafka

class KafkaConsumer[KEY, VALUE](
  override val kafkaConsumerConfiguration: KafkaConsumerConfiguration,
) extends KafkaConsumerTrait[KEY, VALUE]