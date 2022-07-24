package app.tilli.blockchain.service.blockchainsink.sink

import app.tilli.blockchain.codec.BlockchainClasses.TilliAssetContractEvent
import app.tilli.blockchain.service.blockchainsink.Resources
import app.tilli.logging.Logging
import app.tilli.persistence.kafka.KafkaConsumer
import app.tilli.utils.{InputTopic, OutputTopic}
import cats.effect.{Async, Sync}
import fs2.kafka.Deserializer
import mongo4cats.collection.{BulkWriteOptions, ReplaceOptions, WriteCommand}

object AssetContractSink extends Logging {

  def streamAssetContractsIntoDatabase[F[_] : Async](
    resources: Resources[F],
    kafkaConsumer: KafkaConsumer[String, TilliAssetContractEvent],
    inputTopic: InputTopic,
    outputTopicFailure: OutputTopic,
  )(implicit
    valueDeserializer: Deserializer[F, TilliAssetContractEvent],
  ): fs2.Stream[F, Unit] = {
    import cats.implicits._
    import fs2.kafka._
    kafkaConsumer
      .consumerStream
      .subscribeTo(inputTopic.name)
      .records
      .chunks
      .mapAsync(4) { chunk =>
        val batch = CommittableOffsetBatch.fromFoldableMap(chunk)(_.offset)
        val processed = write(resources, chunk.toList.map(_.record.value))
          .flatMap {
            case Right(_) => Sync[F].pure()
            case Left(throwable) =>
              log.error(s"AssetContract: Write failed: ${throwable.getMessage}")
              Sync[F].raiseError(throwable).asInstanceOf[F[Unit]]
          }

        Sync[F].delay(
          log.info(s"AssetContract: Writing batch of size ${chunk.size}: ${
            batch.offsets.lastOption.map(t => s"${t._1}:${t._2.offset()}"
            ).getOrElse("No offset")
          }")) *> processed *> batch.commit
      }
  }

  def write[F[_] : Sync : Async](
    resources: Resources[F],
    data: Seq[TilliAssetContractEvent],
  ): F[Either[Throwable, Boolean]] = {
    import cats.implicits._
    import mongo4cats.collection.operations._
    val commands = data
      .map(assetContractEvent =>
        WriteCommand.ReplaceOne(
          filter = Filter.eq("data.address", assetContractEvent.data.address),
          replacement = assetContractEvent,
          options = ReplaceOptions().upsert(true),
        )
      )
    resources.assetContractEventCollection
      .bulkWrite(commands,
        BulkWriteOptions()
          .ordered(false)
          .bypassDocumentValidation(true)
      )
      .attempt
      .map(_.map(_.wasAcknowledged()))
  }

}
