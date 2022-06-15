package app.tilli.blockchain.asset

import com.ariskk.flink4s._
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer

class AssetContractReaderJob(
  brokers: String,
  topic: String,
) {

  lazy val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment

  def run(): Unit = {
    val groupId: String = this.getClass.getName
    val kafkaConsumer = consumer(brokers, topic, groupId)
    val watermarkStrategy: WatermarkStrategy[String] = WatermarkStrategy.noWatermarks()
    val source: KafkaSource[String] = kafkaConsumer

    val topicSource = env.javaEnv.fromSource(
      source,
      watermarkStrategy,
      "asset_contract_requests"
    )

    topicSource
      .map(r => println(r))

    env.execute
  }


  def consumer(
    brokers: String,
    inputTopic: String,
    groupId: String,
    startingOffsets: OffsetsInitializer = OffsetsInitializer.earliest(),
  ): KafkaSource[String] = KafkaSource.builder[String]
    .setBootstrapServers(brokers)
    .setTopics(inputTopic)
    .setGroupId(groupId)
    .setStartingOffsets(startingOffsets)
    .setValueOnlyDeserializer(new SimpleStringSchema())
    .build()

}
