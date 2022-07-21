package app.tilli.blockchain.service.blockchainsink.config

import app.tilli.persistence.kafka.KafkaConsumerConfiguration
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
  kafkaConsumerConfiguration: KafkaConsumerConfiguration,

  inputTopicTransactionEvent: InputTopic,
  outputTopicFailureEvent: OutputTopic,

  inputTopicDataProviderCursorEvent: InputTopic,
  inputTopicAssetContractEvent: InputTopic,
  inputTopicAnalyticsResultEvent: InputTopic,

  mongoDbConfig: MongoDbConfig,
  mongoDbCollectionTransaction: String,
  mongoDbCollectionDataProviderCursor: String,
  mongoDbCollectionAssetContract: String,
  mongoDbCollectionAnalyticsTransaction: String,
)

object AppConfig {

  implicit val readerInputTopic: ConfigReader[InputTopic] = deriveReader[InputTopic]
  implicit val readerOutputTopic: ConfigReader[OutputTopic] = deriveReader[OutputTopic]
  implicit val readerKafkaConsumerConfiguration: ConfigReader[KafkaConsumerConfiguration] = deriveReader[KafkaConsumerConfiguration]
  implicit val readerMongoDbConfig: ConfigReader[MongoDbConfig] = deriveReader[MongoDbConfig]
  implicit val readerAppConfig: ConfigReader[AppConfig] = deriveReader[AppConfig]

}
