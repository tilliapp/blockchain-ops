package app.tilli.blockchain.service.blockchainreader.config

import app.tilli.api.utils.HttpClientConfig
import app.tilli.persistence.kafka.{KafkaConsumerConfiguration, KafkaProducerConfiguration}
import app.tilli.utils.{InputTopic, OutputTopic}
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

trait RateLimitedResource {
  def minIntervalMs: Int

  def maxConcurrent: Int

  def maxQueued: Int
}

case class RateLimitConfig(
  override val minIntervalMs: Int,
  override val maxConcurrent: Int,
  override val maxQueued: Int,
) extends RateLimitedResource

case class MongoDbConfig(
  url: String,
  db: String,
)

case class AppConfig(
  environment: String,
  httpServerPort: Int,
  httpClientConfig: HttpClientConfig,
  kafkaConsumerConfiguration: KafkaConsumerConfiguration,
  kafkaProducerConfiguration: KafkaProducerConfiguration,

  inputTopicAssetContractRequest: InputTopic,
  outputTopicAssetContract: OutputTopic,
  outputTopicAssetContractEventRequest: OutputTopic,

  inputTopicAssetContractEventRequest: InputTopic,
  outputTopicAssetContractEvent: OutputTopic,

  outputTopicAssetContractRequest: OutputTopic,

  inputTopicAddressContractEvent: InputTopic,
  outputTopicAddressRequest: OutputTopic,

  outputTopicFailureEvent: OutputTopic,

  inputTopicTransactionEvent: InputTopic,

  rateLimitOpenSea: RateLimitConfig,
  rateLimitEtherscan: RateLimitConfig,

  mongoDbConfig: MongoDbConfig,
  mongoDbCollectionDataProviderCursor: String,
  mongoDbCollectionAddressRequestCache: String,
  mongoDbCollectionAssetContract: String,
)

object AppConfig {

  implicit val readerRateLimitOpenSea: ConfigReader[RateLimitConfig] = deriveReader[RateLimitConfig]
  implicit val readerHttpClientConfig: ConfigReader[HttpClientConfig] = deriveReader[HttpClientConfig]
  implicit val readerInputTopic: ConfigReader[InputTopic] = deriveReader[InputTopic]
  implicit val readerOutputTopic: ConfigReader[OutputTopic] = deriveReader[OutputTopic]
  implicit val readerKafkaProducerConfiguration: ConfigReader[KafkaProducerConfiguration] = deriveReader[KafkaProducerConfiguration]
  implicit val readerKafkaConsumerConfiguration: ConfigReader[KafkaConsumerConfiguration] = deriveReader[KafkaConsumerConfiguration]
  implicit val readerMongoDbConfig: ConfigReader[MongoDbConfig] = deriveReader[MongoDbConfig]
  implicit val readerAppConfig: ConfigReader[AppConfig] = deriveReader[AppConfig]

}
