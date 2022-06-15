package app.tilli.persistence.kafka

class KafkaProducer[KEY, VALUE](
  override val kafkaProducerConfiguration: KafkaProducerConfiguration,
) extends KafkaProducerTrait[KEY, VALUE]

