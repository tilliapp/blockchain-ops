package app.tilli.blockchain.service.blockchainsink.sink

import app.tilli.blockchain.codec.BlockchainClasses.TilliAnalyticsResultEvent
import app.tilli.blockchain.service.blockchainsink.Resources
import cats.effect.{Async, Sync}
import mongo4cats.collection.{BulkWriteOptions, ReplaceOptions, WriteCommand}

object AnalyticsSink extends SinkWriter[TilliAnalyticsResultEvent] {

  override def write[F[_] : Sync : Async](
    resources: Resources[F],
    data: Seq[TilliAnalyticsResultEvent],
  ): F[Either[Throwable, Boolean]] = {
    import cats.implicits._
    import mongo4cats.collection.operations._
    val commands = data
      .map(result =>
        WriteCommand.ReplaceOne(
          filter = Filter.eq("data.address", result.data.address)
            .and(Filter.eq("data.tokenId", result.data.tokenId))
            .and(Filter.eq("data.assetContractAddress", result.data.assetContractAddress)),
          replacement = result,
          options = ReplaceOptions().upsert(true),
        )
      )
    resources.analyticsTransactionCollection
      .bulkWrite(commands,
        BulkWriteOptions()
          .ordered(false)
          .bypassDocumentValidation(true)
      )
      .attempt
      .map(_.map(_.wasAcknowledged()))
  }
}
