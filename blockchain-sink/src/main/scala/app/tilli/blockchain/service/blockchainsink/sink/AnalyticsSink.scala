package app.tilli.blockchain.service.blockchainsink.sink

import app.tilli.blockchain.codec.BlockchainClasses.{TilliAnalyticsResultEvent, TilliJsonEvent}
import app.tilli.blockchain.service.blockchainsink.Resources
import cats.effect.{Async, Sync}
import com.mongodb.bulk.BulkWriteResult
import io.circe.syntax.EncoderOps
import mongo4cats.collection.{BulkWriteOptions, ReplaceOptions, WriteCommand}

object AnalyticsSink extends SinkWriter {

  override val concurrency: Int = 8

  override def write[F[_] : Sync : Async](
    resources: Resources[F],
    data: List[TilliJsonEvent],
  ): F[Either[Throwable, Option[BulkWriteResult]]] = {
    import app.tilli.blockchain.codec.BlockchainCodec._
    import cats.implicits._
    import mongo4cats.collection.operations._
    val decoded = data
      .map(j => (j.header.eventId, j.asJson.as[TilliAnalyticsResultEvent]))
      .partition(r => r._2.isLeft)

    val logErrors = Sync[F].delay(decoded._1.foreach(t => log.error(s"Failed to decode eventId=${t._1}")))

    val commands = decoded._2
      .flatMap(_._2.toOption) // We know that the last part of the tuple is a successful decode so this is safe
      .map(result =>
        WriteCommand.ReplaceOne(
          filter = Filter.eq("data.address", result.data.address)
            .and(Filter.eq("data.tokenId", result.data.tokenId))
            .and(Filter.eq("data.assetContractAddress", result.data.assetContractAddress)),
          replacement = result,
          options = ReplaceOptions().upsert(true),
        )
      )

    val writes = {
      if (commands.isEmpty) Sync[F].pure[Option[BulkWriteResult]](None)
      else
        resources
          .analyticsTransactionCollection
          .bulkWrite(commands,
            BulkWriteOptions()
              .ordered(false)
              .bypassDocumentValidation(true)
          )
          .map(Option(_))
    }

    (logErrors *> writes).attempt
  }
}
