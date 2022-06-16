package app.tilli.blockchain.config

import app.tilli.api.utils.HttpClientConfig
import app.tilli.persistence.kafka.KafkaConsumerConfiguration
import app.tilli.utils.InputTopic
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

case class AppConfig(
  environment: String,
  httpClientConfig: HttpClientConfig,
  kafkaConsumerConfiguration: KafkaConsumerConfiguration,
  inputTopic: InputTopic,
)

object AppConfig {

  implicit val readerHttpClientConfig: ConfigReader[HttpClientConfig] = deriveReader[HttpClientConfig]
  implicit val readerInputTopic: ConfigReader[InputTopic] = deriveReader[InputTopic]
  implicit val readerKafkaConsumerConfiguration: ConfigReader[KafkaConsumerConfiguration] = deriveReader[KafkaConsumerConfiguration]
  implicit val readerAppConfig: ConfigReader[AppConfig] = deriveReader[AppConfig]

}
