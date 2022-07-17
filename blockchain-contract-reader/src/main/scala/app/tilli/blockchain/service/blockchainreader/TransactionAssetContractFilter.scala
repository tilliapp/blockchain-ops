package app.tilli.blockchain.service.blockchainreader

import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.codec.BlockchainCodec._
import app.tilli.blockchain.service.StreamTrait
import app.tilli.collection.AssetContractCache
import app.tilli.persistence.kafka.{KafkaConsumer, KafkaProducer}
import app.tilli.utils.{InputTopic, OutputTopic}
import cats.data.EitherT
import cats.effect.{Async, Sync}
import fs2.kafka._
import io.circe.optics.JsonPath.root
import io.circe.syntax.EncoderOps

object TransactionAssetContractFilter extends StreamTrait {

  def assetContractRequestsStream[F[_] : Async](
    r: Resources,
  )(implicit
    valueDeserializer: Deserializer[F, TilliJsonEvent],
    keySerializer: RecordSerializer[F, String],
    valueSerializer: RecordSerializer[F, TilliJsonEvent]
  ): F[Unit] = {
    import cats.implicits._
    val kafkaConsumerConfig = r.appConfig.kafkaConsumerConfiguration
    val kafkaProducerConfig = r.appConfig.kafkaProducerConfiguration
    val kafkaConsumer = new KafkaConsumer[String, TilliJsonEvent](kafkaConsumerConfig, r.sslConfig)
    val kafkaProducer = new KafkaProducer[String, TilliJsonEvent](kafkaProducerConfig, r.sslConfig)
    val inputTopic = r.appConfig.inputTopicTransactionEvent
    val outputTopicAssetContractRequest = r.appConfig.outputTopicAssetContractRequest
    val outputTopicFailure = r.appConfig.outputTopicFailureEvent

    import fs2.kafka._
    val stream =
      kafkaProducer
        .producerStream
        .flatMap(producer =>
          kafkaConsumer
            .consumerStream
            .subscribeTo(inputTopic.name)
            .records
            .chunks
            .mapAsync(8) { chunk =>
              val batch = CommittableOffsetBatch.fromFoldableMap(chunk)(_.offset)
              val processed = {
                chunk.map { committable =>
                  val processedRecord = processRecord(committable.record, r.assetContractCache).asInstanceOf[F[Either[HttpClientErrorTrait, Option[AssetContractRequest]]]]
                  processedRecord.map {
                    case Right(result) => toProducerRecords(committable, result, outputTopicAssetContractRequest)
                    case Left(errorTrait) => handleDataProviderError(committable, errorTrait, inputTopic, outputTopicFailure, r.assetContractSource)
                  } //.flatTap(r => Sync[F].delay(log.info(s"eventId=${committable.record.value.header.eventId}: Emitted=${r.records.size}. Committed=${committable.offset.topicPartition}:${committable.offset.offsetAndMetadata.offset}")))
                }.sequence
              }
              processed *> batch.commit
            }
        )
    stream.compile.drain
  }

  def processRecord[F[_] : Sync : Async](
    record: ConsumerRecord[String, TilliJsonEvent],
    assetContractCache: AssetContractCache[F],
  ): F[Either[Throwable, Option[AssetContractRequest]]] = {
    val chain = {
      for {
        contract <- EitherT(Sync[F].pure(root.assetContractAddress.string.getOption(record.value.data).toRight(new IllegalStateException(s"Could not find asset contract from record: ${
          record.value.data.noSpaces
        }"))))
        lookup <- EitherT(assetContractCache.lookup(contract))
      } yield {
        if (lookup.isEmpty) Some(AssetContractRequest(AssetContract(address = contract)))
        else None
      }
    }
    chain.value
  }

  def toProducerRecords[F[_]](
    committable: CommittableConsumerRecord[F, String, TilliJsonEvent],
    result: Option[AssetContractRequest],
    assetContractEventRequestTopic: OutputTopic,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    val assetContractProducerRecord = result.map {
      assetContractRequest =>
        val tilliAssetContractRequestEvent = TilliAssetContractRequestEvent(assetContractRequest)
        val key = tilliAssetContractRequestEvent.data.assetContract.address
        val tilliJsonEvent = TilliJsonEvent(
          header = tilliAssetContractRequestEvent.header,
          data = tilliAssetContractRequestEvent.data.asJson
        )
        ProducerRecord(
          assetContractEventRequestTopic.name,
          key,
          tilliJsonEvent,
        )
    }
    ProducerRecords(
      records = assetContractProducerRecord,
      committable.offset
    )
  }

  override def toRetryPageProducerRecords[F[_]](
    record: ConsumerRecord[String, TilliJsonEvent],
    offset: CommittableOffset[F],
    inputTopic: InputTopic,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = ???

}
