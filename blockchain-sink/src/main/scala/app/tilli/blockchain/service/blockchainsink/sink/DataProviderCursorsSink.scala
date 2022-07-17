package app.tilli.blockchain.service.blockchainsink.sink

import app.tilli.blockchain.codec.BlockchainClasses.{DataProviderCursor, DataProviderCursorRecord, TilliJsonEvent}
import app.tilli.blockchain.codec.BlockchainCodec.codecDataProviderCursor
import app.tilli.blockchain.service.blockchainsink.Resources
import app.tilli.logging.Logging
import app.tilli.persistence.kafka.KafkaConsumer
import app.tilli.utils.{InputTopic, OutputTopic}
import cats.effect.{Async, Sync}
import fs2.Chunk
import fs2.kafka.{CommittableConsumerRecord, Deserializer}
import mongo4cats.collection.{BulkWriteOptions, ReplaceOptions, WriteCommand}

object DataProviderCursorsSink extends Logging {

  def streamDataProviderCursorsIntoDatabase[F[_] : Async](
    resources: Resources[F],
    kafkaConsumer: KafkaConsumer[String, TilliJsonEvent],
    inputTopicCursors: InputTopic,
    outputTopicFailure: OutputTopic,
  )(implicit
    valueDeserializer: Deserializer[F, TilliJsonEvent],
  ): fs2.Stream[F, Unit] = {
    import cats.implicits._
    import fs2.kafka._
    kafkaConsumer
      .consumerStream
      .subscribeTo(inputTopicCursors.name)
      .records
      .chunks
      .mapAsync(8) { chunk =>
        val batch = CommittableOffsetBatch.fromFoldableMap(chunk)(_.offset)
        val processed = writeDataProviderCursors(resources, transformDataProviderCursorRecords(chunk))
          .flatMap {
            case Right(_) => Sync[F].pure()
            case Left(throwable) =>
              log.error(s"DataProviderCursor: Write failed: ${throwable.getMessage}")
              Sync[F].raiseError(throwable).asInstanceOf[F[Unit]]
          }

        Sync[F].delay(
          log.info(s"DataProviderCursor: Writing batch of size ${chunk.size}: ${
            batch.offsets.lastOption.map(t => s"${t._1}:${t._2.offset()}"
            ).getOrElse("No offset")
          }")) *> processed *> batch.commit
      }
  }

  def writeDataProviderCursors[F[_] : Sync : Async](
    resources: Resources[F],
    data: Seq[DataProviderCursor],
  ): F[Either[Throwable, Boolean]] = {
    import cats.implicits._
    import mongo4cats.collection.operations._
    val commands = data
      .map(DataProviderCursorRecord(_))
      .map(cursor =>
        WriteCommand.ReplaceOne(
          filter = Filter.eq("key", cursor.key),
          replacement = cursor,
          options = ReplaceOptions().upsert(true),
        )
      )
    resources.dataProviderCursorCollection
      .bulkWrite(commands,
        BulkWriteOptions()
          .ordered(false)
          .bypassDocumentValidation(true)
      )
      .attempt
      .map(_.map(_.wasAcknowledged()))
  }

  def transformDataProviderCursorRecords[F[_]](
    chunkRecords: Chunk[CommittableConsumerRecord[F, String, TilliJsonEvent]],
  ): List[DataProviderCursor] = {
    chunkRecords
      .toList
      .map(_.record.value.data)
      .map { json =>
        val Right(record) = json.as[DataProviderCursor](codecDataProviderCursor)
        record
      }
  }

}
