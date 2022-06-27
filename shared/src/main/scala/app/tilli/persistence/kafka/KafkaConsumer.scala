package app.tilli.persistence.kafka

class KafkaConsumer[KEY, VALUE](
  override val kafkaConsumerConfiguration: KafkaConsumerConfiguration,
  sslConfig: Option[Map[String, String]] = None,
) extends KafkaConsumerTrait[KEY, VALUE] {

  override def withConsumerProperties: Map[String, String] =
    super.withConsumerProperties ++ sslConfig.getOrElse(Map.empty)
}