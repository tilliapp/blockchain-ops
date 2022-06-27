package app.tilli.blockchain.service.blockchainsink.config

import app.tilli.persistence.kafka.KafkaConsumerConfiguration
import app.tilli.utils.{InputTopic, OutputTopic}
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

case class MongoDbConfig(
  url: String,
)

case class AppConfig(
  environment: String,
  kafkaConsumerConfiguration: KafkaConsumerConfiguration,

  inputTopicTransactionEvent: InputTopic,
  outputTopicFailureEvent: OutputTopic,

  mongoDbConfig: MongoDbConfig,
)

object AppConfig {

  implicit val readerInputTopic: ConfigReader[InputTopic] = deriveReader[InputTopic]
  implicit val readerOutputTopic: ConfigReader[OutputTopic] = deriveReader[OutputTopic]
  implicit val readerKafkaConsumerConfiguration: ConfigReader[KafkaConsumerConfiguration] = deriveReader[KafkaConsumerConfiguration]
  implicit val readerMongoDbConfig: ConfigReader[MongoDbConfig] = deriveReader[MongoDbConfig]
  implicit val readerAppConfig: ConfigReader[AppConfig] = deriveReader[AppConfig]

}
