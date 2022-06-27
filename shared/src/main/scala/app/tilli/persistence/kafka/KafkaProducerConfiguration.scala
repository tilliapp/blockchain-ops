package app.tilli.persistence.kafka

trait KafkaProducerConfigurationTrait extends KafkaClientConfig {

  def bootstrapServers: String

}

case class KafkaProducerConfiguration(
  override val bootstrapServers: String,
) extends KafkaProducerConfigurationTrait
