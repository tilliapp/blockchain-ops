package app.tilli.blockchain.service.analytics.config

import app.tilli.api.utils.HttpClientConfig
import app.tilli.persistence.kafka.{KafkaConsumerConfiguration, KafkaProducerConfiguration}
import app.tilli.utils.{InputTopic, OutputTopic}
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

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

  inputTopicAnalyticsAddressRequestEvent: InputTopic,
  inputTopicMongodbTransactionEvent: InputTopic,
  outputTopicAnalyticsAddressResult: OutputTopic,
  outputTopicFailureEvent: OutputTopic,
  outputTopicAnalyticsAddressRequestEvent: OutputTopic,

  mongoDbConfig: MongoDbConfig,
  mongoDbCollectionTransaction: String,
  mongoDbCollectionAssetContract: String,
)

object AppConfig {

  implicit val readerHttpClientConfig: ConfigReader[HttpClientConfig] = deriveReader[HttpClientConfig]
  implicit val readerInputTopic: ConfigReader[InputTopic] = deriveReader[InputTopic]
  implicit val readerOutputTopic: ConfigReader[OutputTopic] = deriveReader[OutputTopic]
  implicit val readerKafkaProducerConfiguration: ConfigReader[KafkaProducerConfiguration] = deriveReader[KafkaProducerConfiguration]
  implicit val readerKafkaConsumerConfiguration: ConfigReader[KafkaConsumerConfiguration] = deriveReader[KafkaConsumerConfiguration]
  implicit val readerMongoDbConfig: ConfigReader[MongoDbConfig] = deriveReader[MongoDbConfig]
  implicit val readerAppConfig: ConfigReader[AppConfig] = deriveReader[AppConfig]

}
