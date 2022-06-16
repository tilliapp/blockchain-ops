package app.tilli.persistence.kafka

trait KafkaConsumerConfigurationTrait extends KafkaClientConfig {
  def bootstrapServers: String

  def groupId: String

  def autoOffsetResetConfig: String

  def batchSize: Int

  def batchDurationMs: Long

}

case class KafkaConsumerConfiguration(
  override val bootstrapServers: String,
  override val groupId: String,
  override val autoOffsetResetConfig: String,
  override val batchSize: Int,
  override val batchDurationMs: Long,
) extends KafkaConsumerConfigurationTrait
