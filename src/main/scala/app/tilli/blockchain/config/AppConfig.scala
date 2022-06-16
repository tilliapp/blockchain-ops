package app.tilli.blockchain.config

import app.tilli.api.utils.HttpClientConfig
import app.tilli.persistence.kafka.KafkaConsumerConfiguration
import app.tilli.utils.InputTopic
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

trait RateLimitedResource {
  def minIntervalMs: Int

  def maxConcurrent: Int

  def maxQueued: Int
}

case class RateLimitOpenSea(
  override val minIntervalMs: Int,
  override val maxConcurrent: Int,
  override val maxQueued: Int,
) extends RateLimitedResource

case class AppConfig(
  environment: String,
  httpClientConfig: HttpClientConfig,
  kafkaConsumerConfiguration: KafkaConsumerConfiguration,
  inputTopic: InputTopic,
  rateLimitOpenSea: RateLimitOpenSea,
)

object AppConfig {

  implicit val readerRateLimitOpenSea: ConfigReader[RateLimitOpenSea] = deriveReader[RateLimitOpenSea]
  implicit val readerHttpClientConfig: ConfigReader[HttpClientConfig] = deriveReader[HttpClientConfig]
  implicit val readerInputTopic: ConfigReader[InputTopic] = deriveReader[InputTopic]
  implicit val readerKafkaConsumerConfiguration: ConfigReader[KafkaConsumerConfiguration] = deriveReader[KafkaConsumerConfiguration]
  implicit val readerAppConfig: ConfigReader[AppConfig] = deriveReader[AppConfig]

}
