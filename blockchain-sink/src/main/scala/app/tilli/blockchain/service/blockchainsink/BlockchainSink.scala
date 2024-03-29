package app.tilli.blockchain.service.blockchainsink

import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.service.blockchainsink.sink.{AnalyticsSink, AssetContractSink, DataProviderCursorsSink, TransactionsSink}
import app.tilli.logging.Logging
import app.tilli.persistence.kafka.KafkaConsumer
import cats.Parallel
import cats.effect.{Async, Sync}
import fs2.kafka._

object BlockchainSink extends Logging {

  def streamToSink[F[_] : Async : Sync : Parallel](
    r: Resources[F],
  )(implicit
    valueDeserializer: Deserializer[F, TilliJsonEvent],
    valueDeserializer2: Deserializer[F, TilliAssetContractEvent],
    valueDeserializer3: Deserializer[F, TilliAnalyticsResultEvent],
  ): F[Unit] = {
    val kafkaConsumerConfig = r.appConfig.kafkaConsumerConfiguration
    val kafkaConsumer = new KafkaConsumer[String, TilliJsonEvent](kafkaConsumerConfig, r.sslConfig)
    val kafkaConsumer2 = new KafkaConsumer[String, TilliAssetContractEvent](kafkaConsumerConfig, r.sslConfig)
    val kafkaConsumer3 = new KafkaConsumer[String, TilliJsonEvent](kafkaConsumerConfig, r.sslConfig)
    val inputTopicTransactions = r.appConfig.inputTopicTransactionEvent
    val inputTopicCursors = r.appConfig.inputTopicDataProviderCursorEvent
    val inputTopicAssertContract = r.appConfig.inputTopicAssetContractEvent
    val inputTopicAnalyticsResultEvent = r.appConfig.inputTopicAnalyticsResultEvent
    val outputTopicFailure = r.appConfig.outputTopicFailureEvent

    import cats.implicits._
    val transactions: F[Unit] = TransactionsSink.streamIntoDatabase(r, kafkaConsumer, inputTopicTransactions, outputTopicFailure)
      .compile
      .drain

    val dataProviderCursors: F[Unit] = DataProviderCursorsSink.streamDataProviderCursorsIntoDatabase(r, kafkaConsumer, inputTopicCursors, outputTopicFailure)
      .compile
      .drain

    val assetContracts: F[Unit] = AssetContractSink.streamAssetContractsIntoDatabase(r, kafkaConsumer2, inputTopicAssertContract, outputTopicFailure)
      .compile
      .drain

    val analyticsResult: F[Unit] = AnalyticsSink.streamIntoDatabase(r, kafkaConsumer3, inputTopicAnalyticsResultEvent, outputTopicFailure)
      .compile
      .drain

    transactions &> dataProviderCursors &> assetContracts &> analyticsResult
  }

}
