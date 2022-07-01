package app.tilli.blockchain.service.blockchainsink

import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.codec.BlockchainCodec._
import app.tilli.logging.Logging
import app.tilli.persistence.kafka.KafkaConsumer
import app.tilli.utils.{InputTopic, OutputTopic}
import cats.Parallel
import cats.effect.{Async, Sync}
import fs2.Chunk
import fs2.kafka._
import io.circe.Json
import io.circe.optics.JsonPath.root
import mongo4cats.collection.{BulkWriteOptions, ReplaceOptions, WriteCommand}

import java.time.Instant
import java.util.UUID

object BlockchainSink extends Logging {

  def streamToSink[F[_] : Async : Sync : Parallel](
    r: Resources[F],
  )(implicit
    valueDeserializer: Deserializer[F, TilliJsonEvent],
  ): F[Unit] = {
    val kafkaConsumerConfig = r.appConfig.kafkaConsumerConfiguration
    val kafkaConsumer = new KafkaConsumer[String, TilliJsonEvent](kafkaConsumerConfig, r.sslConfig)
    val inputTopicTransactions = r.appConfig.inputTopicTransactionEvent
    val inputTopicCursors = r.appConfig.inputTopicDataProviderCursorEvent
    val outputTopicFailure = r.appConfig.outputTopicFailureEvent

    import cats.implicits._
    val transactions: F[Unit] = streamTransactionsIntoDatabase(r, kafkaConsumer, inputTopicTransactions, outputTopicFailure)
      .compile
      .drain

    val dataProviderCursors: F[Unit] = streamDataProviderCursorsIntoDatabase(r, kafkaConsumer, inputTopicCursors, outputTopicFailure)
      .compile
      .drain

    transactions &> dataProviderCursors
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
          key = getKey(json).get,// TODO: This is gonna blow up for sure
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

  def toProducerRecords[F[_]](
    offset: CommittableOffset[F],
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    ProducerRecords(
      None,
      offset
    )
  }

  def toErrorProducerRecords[F[_]](
    record: ConsumerRecord[String, TilliJsonEvent],
    offset: CommittableOffset[F],
    error: Json,
    outputTopic: OutputTopic,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    val errorEvent = record.value.copy(
      header = record.value.header.copy(
        eventTimestamp = Instant.now().toEpochMilli,
        eventId = UUID.randomUUID(),
        origin = record.value.header.origin
      ),
      data = error,
    )
    ProducerRecords(
      List(ProducerRecord(outputTopic.name, record.key, errorEvent)),
      offset
    )
  }

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
          filter = Filter.eq("key", cursor.key)
            .and(Filter.lt("createdAt", cursor.data.createdAt)),
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
