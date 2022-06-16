package app.tilli.blockchain.config

import app.tilli.persistence.kafka.KafkaConsumerConfiguration
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

case class AppConfig(
  environment: String,
  kafkaConsumerConfiguration: KafkaConsumerConfiguration,
)

object AppConfig {

  implicit val readerKafkaConsumerConfiguration: ConfigReader[KafkaConsumerConfiguration] = deriveReader[KafkaConsumerConfiguration]
  implicit val readerAppConfig: ConfigReader[AppConfig] = deriveReader[AppConfig]

}
