package app.tilli.blockchain.service.analytics

import app.tilli.blockchain.codec.BlockchainClasses
import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.codec.BlockchainConfig.{DataTypeAnalyticsRequest, NullAddress}
import app.tilli.blockchain.service.StreamTrait
import app.tilli.collection.AssetContractCache
import app.tilli.persistence.kafka.{KafkaConsumer, KafkaProducer}
import app.tilli.utils.{InputTopic, OutputTopic}
import cats.effect.{Async, Sync}
import cats.implicits._
import fs2.kafka._
import io.circe.optics.JsonPath.root
import io.circe.syntax.EncoderOps
import io.circe.{Encoder, Json}

import scala.concurrent.duration.DurationLong

object AnalyticsTrigger extends StreamTrait {

  def stream[F[_] : Sync : Async](
    r: Resources[F],
  )(implicit
    valueDeserializer: Deserializer[F, Json],
    keySerializer: RecordSerializer[F, String],
    valueSerializer: RecordSerializer[F, TilliJsonEvent]
  ): F[Unit] = {
    val kafkaConsumerConfig = r.appConfig.kafkaConsumerConfiguration
    val kafkaProducerConfig = r.appConfig.kafkaProducerConfiguration
    val kafkaConsumer = new KafkaConsumer[String, Json](kafkaConsumerConfig, r.sslConfig)
    val kafkaProducer = new KafkaProducer[String, TilliJsonEvent](kafkaProducerConfig, r.sslConfig)
    val inputTopic = r.appConfig.inputTopicMongodbTransactionEvent
    val outputTopic = r.appConfig.outputTopicAnalyticsAddressRequestEvent

    import app.tilli.blockchain.codec.BlockchainCodec._
    import fs2.kafka._
    val stream =
      kafkaProducer
        .producerStream
        .flatMap(producer =>
          kafkaConsumer
            .consumerStream
            .subscribeTo(inputTopic.name)
            .partitionedRecords
            .map { partitionStream =>
              partitionStream.evalMap { committable =>
                getAddressFromDebeziumTransactionEvent(committable.record.value) match {
                  case Left(err) =>
                    Sync[F].delay(log.error("An error occurred while processing debezium event. Continuing processing", err)) *>
                      Sync[F].pure(toProducerRecords(committable.offset, List.empty, outputTopic))
                  case Right(addresses) =>
                    val lookup = addresses
                      .filter(_ != NullAddress)
                      .map(address => isAssetContract(address, r.assetContractCache))
                      .sequence
                      .map(_.sequence)
                      .map(e => e.map(_.zip(addresses)))
                      .map(_.map(_.filter(!_._1))) // only keep non asset contracts
                      .map(_.map(_.map(_._2)))
                      .asInstanceOf[F[Either[Throwable, List[String]]]]

                    lookup.flatMap {
                      case Left(err) =>
                        Sync[F].delay(log.error(s"An error occurred while looking up addresses: $addresses", err)) *>
                          Sync[F].pure(toProducerRecords(committable.offset, List.empty, outputTopic))
                      case Right(wallets) => Sync[F].pure(toProducerRecords(committable.offset, wallets, outputTopic))
                    }
                }
              }
            }.parJoinUnbounded
            .through(fs2.kafka.KafkaProducer.pipe(kafkaProducer.producerSettings, producer))
            .map(_.passthrough)
            .through(commitBatchWithin(kafkaConsumerConfig.batchSize, kafkaConsumerConfig.batchDurationMs.milliseconds))
        )
    stream.compile.drain

  }

  def getAddressFromDebeziumTransactionEvent(json: Json): Either[Throwable, List[String]] = {
    root.payload.after.string.getOption(json)
      .toRight(new IllegalStateException(s"No payload.after field was found in json: $json"))
      .flatMap { js =>
        io.circe.parser
          .parse(js)
          .map(j =>
            List(
              root.data.fromAddress.string.getOption(j),
              root.data.toAddress.string.getOption(j),
            ).flatten
          )
      }
  }

  def isAssetContract[F[_] : Sync : Async](
    contract: String,
    assetContractCache: AssetContractCache[F],
  ): F[Either[Throwable, Boolean]] =
    assetContractCache
      .lookup(contract)
      .map(_.map(_.nonEmpty))

  def toProducerRecords[F[_]](
    offset: CommittableOffset[F],
    addresses: List[String],
    outputTopic: OutputTopic,
  )(implicit
    encoder: Encoder[AnalyticsResult],
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    import app.tilli.blockchain.codec.BlockchainCodec.codecAnalyticsRequest
    val producerRecords = addresses.map { address =>
      val event = TilliJsonEvent(
        header = BlockchainClasses.Header(DataTypeAnalyticsRequest),
        data = AnalyticsRequest(address = address, assetContractAddress = None, tokenId = None).asJson
      )
      ProducerRecord(outputTopic.name, address, event)
    }
    ProducerRecords(
      producerRecords,
      offset
    )
  }

  override def toRetryPageProducerRecords[F[_]](
    record: ConsumerRecord[String, TilliJsonEvent],
    offset: CommittableOffset[F],
    inputTopic: InputTopic,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = ???

}
