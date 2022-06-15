package app.tilli.persistence.kafka

trait KafkaConsumerConfigurationTrait extends KafkaClientConfig {
  def bootstrapServers: String

  def groupId: String

  def autoOffsetResetConfig: String

  def batchSize: Option[Int]

  def batchDurationMs: Option[Long]

}

case class KafkaConsumerConfiguration(
  override val bootstrapServers: String,
  override val groupId: String,
  override val autoOffsetResetConfig: String,
  override val batchSize: Option[Int],
  override val batchDurationMs: Option[Long],
) extends KafkaConsumerConfigurationTrait
