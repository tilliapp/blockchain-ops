package app.tilli.blockchain.service.blockchainsink.sink

import app.tilli.blockchain.codec.BlockchainClasses.{TilliJsonEvent, TransactionRecord, TransactionRecordData}
import app.tilli.blockchain.codec.BlockchainCodec.codecTransactionRecordData
import app.tilli.blockchain.service.blockchainsink.Resources
import cats.effect.{Async, Sync}
import com.mongodb.bulk.BulkWriteResult
import io.circe.Json
import io.circe.optics.JsonPath.root
import mongo4cats.collection.{BulkWriteOptions, ReplaceOptions, WriteCommand}

object TransactionsSink extends SinkWriter[TilliJsonEvent] {

  override val concurrency: Int = 8

  def transform[F[_]](
    chunkRecords: List[TilliJsonEvent],
  ): Either[Throwable, List[TransactionRecord]] = {
    import cats.implicits._
    chunkRecords
      .map(event =>
        for {
          record <- event.data.as[TransactionRecordData](codecTransactionRecordData)
          key <- getKey(event.data).toRight(new IllegalStateException(s"No key could be extracted from event with header: ${event.header}"))
        } yield
          TransactionRecord(
            key = key,
            data = record,
          )
      ).sequence
  }

  def getKey(json: Json): Option[String] = {
    for {
      transactionHash <- root.transactionHash.string.getOption(json)
      transactionOffset <- root.transactionOffset.long.getOption(json)
      logOffset <- root.logOffset.long.getOption(json)
    } yield s"$transactionHash-$transactionOffset-$logOffset"
  }

  override def write[F[_] : Sync : Async](
    resources: Resources[F],
    data: List[TilliJsonEvent],
  ): F[Either[Throwable, BulkWriteResult]] = {
    import cats.implicits._
    import mongo4cats.collection.operations._

    transform(data) match {
      case Left(err) => Sync[F].raiseError(err)
      case Right(td) =>
        val commands = td.map(t =>
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
    }
  }

}
