package app.tilli.blockchain.service.blockchainsink.sink

import app.tilli.blockchain.codec.BlockchainClasses.{TilliJsonEvent, TransactionRecord, TransactionRecordData}
import app.tilli.blockchain.codec.BlockchainCodec.codecTransactionRecordData
import app.tilli.blockchain.service.blockchainsink.Resources
import app.tilli.logging.Logging
import app.tilli.persistence.kafka.KafkaConsumer
import app.tilli.utils.{InputTopic, OutputTopic}
import cats.effect.{Async, Sync}
import fs2.Chunk
import fs2.kafka.{CommittableConsumerRecord, Deserializer}
import io.circe.Json
import io.circe.optics.JsonPath.root
import mongo4cats.collection.{BulkWriteOptions, ReplaceOptions, WriteCommand}

object TransactionsSink extends Logging {

  def streamTransactionsIntoDatabase[F[_] : Async](
    resources: Resources[F],
    kafkaConsumer: KafkaConsumer[String, TilliJsonEvent],
    inputTopicTransactions: InputTopic,
    outputTopicFailure: OutputTopic,
  )(implicit
    valueDeserializer: Deserializer[F, TilliJsonEvent],
  ): fs2.Stream[F, Unit] = {
    import cats.implicits._
    import fs2.kafka._
    kafkaConsumer
      .consumerStream
      .subscribeTo(inputTopicTransactions.name)
      .records
      .chunks
      .mapAsync(8) { chunk =>
        val batch = CommittableOffsetBatch.fromFoldableMap(chunk)(_.offset)
        val processed = writeTransactions(resources, transform(chunk))
          .flatMap {
            case Right(_) => Sync[F].pure()
            case Left(throwable) =>
              log.error(s"Transactions: Write failed: ${throwable.getMessage}")
              Sync[F].raiseError(throwable).asInstanceOf[F[Unit]]
          }

        Sync[F].delay(
          log.info(s"Transactions: Writing batch of size ${chunk.size}: ${
            batch.offsets.lastOption.map(t => s"${t._1}:${t._2.offset()}"
            ).getOrElse("No offset")
          }")) *> processed *> batch.commit
      }
  }

  def transform[F[_]](
    chunkRecords: Chunk[CommittableConsumerRecord[F, String, TilliJsonEvent]],
  ): List[TransactionRecord] = {
    chunkRecords
      .toList
      .map(_.record.value.data)
      .map { json =>
        val Right(record) = json.as[TransactionRecordData](codecTransactionRecordData)
        TransactionRecord(
          key = getKey(json).get, // TODO: This is gonna blow up for sure
          data = record,
        )
      }
  }

  def getKey(json: Json): Option[String] = {
    for {
      transactionHash <- root.transactionHash.string.getOption(json)
      transactionOffset <- root.transactionOffset.long.getOption(json)
      logOffset <- root.logOffset.long.getOption(json)
    } yield s"$transactionHash-$transactionOffset-$logOffset"
  }

  def writeTransactions[F[_] : Sync : Async](
    resources: Resources[F],
    data: Seq[TransactionRecord],
  ): F[Either[Throwable, Boolean]] = {
    import cats.implicits._
    import mongo4cats.collection.operations._

    val commands = data.map(t =>
      WriteCommand.ReplaceOne(
        filter = Filter.eq("key", t.key),
        replacement = t,
        options = ReplaceOptions().upsert(true),
      )
    )
    resources.transactionCollection
      .bulkWrite(commands,
        BulkWriteOptions()
          .ordered(false)
          .bypassDocumentValidation(true)
      )
      .attempt
      .map(_.map(_.wasAcknowledged()))
  }

}
