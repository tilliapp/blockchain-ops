package app.tilli.persistence.kafka

class KafkaProducer[KEY, VALUE](
  override val kafkaProducerConfiguration: KafkaProducerConfiguration,
  sslConfig: Option[Map[String, String]] = None,
) extends KafkaProducerTrait[KEY, VALUE] with SslConfig {

  override def withProducerProperties: Map[String, String] =
    super.withProducerProperties ++ sslConfig.map(processSslConfig).getOrElse(Map.empty)
}