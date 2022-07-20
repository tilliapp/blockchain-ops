package app.tilli.blockchain.analytics.service

import app.tilli.blockchain.codec.BlockchainClasses.Doc
import app.tilli.blockchain.codec.BlockchainConfig.{ContractTypes, NullAddress}
import app.tilli.logging.Logging
import cats.MonadThrow
import cats.effect.kernel.Sync
import cats.effect.{Async, IO}
import cats.implicits._
import mongo4cats.bson.Document
import mongo4cats.collection.MongoCollection
import mongo4cats.collection.operations.{Aggregate, Filter, Projection}

import java.time.Duration


case class Asset(
  address: String,
  tokenId: Option[String],
  assetContractAddress: Option[String],
  assetContractName: Option[String],
  assetContractType: Option[String],
  count: Option[Int],
  duration: Option[Long],
  originatedFromNullAddress: Boolean,
  transactions: Option[List[String]],
)

object NftHolding extends Logging {

  def stream[F[_] : Sync](
    r: Resources,
  ): F[Unit] = {
    //    val address = "0xbecb05b9335fc0c53aeab1c09733cdf9a0cde85e"
    val address = "0x1662d66493ba8d210c2d129fbab0e8de04fe9ede"
    //    val address = "0x6eb534ed1329e991842b55be375abc63fe7c0e2b"

    val stream = loadByAddress(address, r.transactionCollection)
      //      .flatTap(r => IO(r.map(e => { e.foreach(println)})))
      .flatMap {
        case Left(err) => IO.raiseError(err)
        case Right(res) => IO(tally(address, res))
      }
      .flatTap {
        case Left(err) => IO(log.error(s"An error occurred while processing address: $address", err))
        case Right(res) => IO(res.foreach(println))
      }

    stream.asInstanceOf[F[Unit]]
  }

  def loadByAddress[F[_] : Async](
    address: String,
    collection: MongoCollection[F, Doc],
  )(implicit
    F: MonadThrow[F],
  ): F[Either[Throwable, Iterable[Doc]]] = {

    val ERC20 = ContractTypes.ERC20.toString
    val aggregate = Aggregate
      .matchBy(Filter.eq("data.toAddress", address)
        .or(Filter.eq("data.fromAddress", address))
      )
      .project(Projection.include("data"))
      .lookup(
        from = "asset_contract",
        localField = "data.assetContractAddress",
        foreignField = "data.address",
        as = "schema",
      )
      .project(
        Projection
          .include("data.transactionHash")
          .include("data.toAddress")
          .include("data.fromAddress")
          .include("data.tokenId")
          .include("data.assetContractAddress")
          .include("data.assetContractName")
          //          .include("data.assetContractType")
          .include("data.transactionTime")
          //          .include("data.totalPrice")
          .computed("assetContractType", Document("$arrayElemAt" -> List("$schema.data.schema", 0)))
      )
      .matchBy(Filter.ne("assetContractType", ERC20))

    val attempt = collection
      .aggregate[Doc](aggregate)
      .all
      .attempt

    attempt
  }

  def tally(
    address: String,
    docs: Iterable[Doc],
  ): Either[IllegalStateException, List[Asset]] = {
    val tokens = docs
      .groupBy(r => s"${r.data.assetContractAddress}-${r.data.tokenId}")
      .values
      .toList
      .filter(_.exists(r => r.data.toAddress.map(_.toLowerCase) != r.data.fromAddress.map(_.toLowerCase)))
      .map(_.toList.sortBy(_.data.transactionTime))
      .sortBy(e => e.headOption.map(_.data.transactionTime))
      .map { docs =>
        val firstRecord = docs.head
        val transactions = docs
          .map { doc =>
            val sign = if (doc.data.toAddress.contains(address)) +1 else -1
            val transactionTime = doc.data.transactionTime
            val transactionHash = doc.data.transactionHash
            (sign, transactionTime, transactionHash)
          }
          .grouped(2)
          .toList
          .map { g =>
            val transactions = g.flatMap(_._3)
            g.size match {
              case 1 => Right((g.head._1, None, transactions))
              case 2 =>
                val duration = for {
                  start <- g.head._2
                  end <- g(1)._2
                } yield Duration.between(start, end).toDays
                Right((g.map(_._1).sum, duration, transactions))
              case _ => Left(new IllegalStateException("invalid number of transactions in group of 2"))
            }
          }
          .map(_.map(res =>
            Asset(
              address = address,
              tokenId = firstRecord.data.tokenId,
              assetContractAddress = firstRecord.data.assetContractAddress,
              assetContractName = firstRecord.data.assetContractName,
              assetContractType = firstRecord.assetContractType,
              count = Option(res._1),
              duration = res._2,
              originatedFromNullAddress = firstRecord.data.fromAddress.contains(NullAddress),
              transactions = Some(res._3),
            )
          ))
        transactions.sequence
      }
    tokens
      .sequence
      .map(_.flatten)
  }

}
