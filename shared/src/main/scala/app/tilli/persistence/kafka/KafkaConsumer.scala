package app.tilli.persistence.kafka

class KafkaConsumer[KEY, VALUE](
  override val kafkaConsumerConfiguration: KafkaConsumerConfiguration,
  sslConfig: Option[Map[String, String]] = None,
) extends KafkaConsumerTrait[KEY, VALUE] with SslConfig {

  override def withConsumerProperties: Map[String, String] =
    super.withConsumerProperties ++ sslConfig.map(processSslConfig).getOrElse(Map.empty)
}