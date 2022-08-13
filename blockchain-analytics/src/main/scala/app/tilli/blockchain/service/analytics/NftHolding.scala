package app.tilli.blockchain.service.analytics

import app.tilli.blockchain.codec.BlockchainClasses
import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.codec.BlockchainConfig.{ContractTypes, DataAnalyticsResultStatsV1Event, NullAddress}
import app.tilli.blockchain.dataprovider.TilliDataProvider
import app.tilli.blockchain.service.StreamTrait
import app.tilli.persistence.kafka.{KafkaConsumer, KafkaProducer}
import app.tilli.utils.{InputTopic, OutputTopic}
import cats.MonadThrow
import cats.effect.{Async, Sync}
import cats.implicits._
import fs2.kafka._
import io.circe.Encoder
import io.circe.syntax.EncoderOps
import mongo4cats.bson.Document
import mongo4cats.collection.MongoCollection
import mongo4cats.collection.operations.{Aggregate, Filter, Projection}

import java.time.{Duration, Instant}
import scala.concurrent.duration.DurationLong

object NftHolding extends StreamTrait {

  def stream[F[_] : Sync : Async](
    r: Resources[F],
  )(implicit
    valueDeserializer: Deserializer[F, TilliAnalyticsAddressRequestEvent],
    keySerializer: RecordSerializer[F, String],
    valueSerializer: RecordSerializer[F, TilliJsonEvent]
  ): F[Unit] = {
    val kafkaConsumerConfig = r.appConfig.kafkaConsumerConfiguration
    val kafkaProducerConfig = r.appConfig.kafkaProducerConfiguration
    val kafkaConsumer = new KafkaConsumer[String, TilliAnalyticsAddressRequestEvent](kafkaConsumerConfig, r.sslConfig)
    val kafkaProducer = new KafkaProducer[String, TilliJsonEvent](kafkaProducerConfig, r.sslConfig)
    val inputTopic = r.appConfig.inputTopicAnalyticsAddressRequestEvent
    val outputTopic = r.appConfig.outputTopicAnalyticsAddressResult
    val outputTopicFailure = r.appConfig.outputTopicFailureEvent

    import app.tilli.blockchain.codec.BlockchainCodec._
    import cats.implicits._
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
                val address = committable.record.value.data.address
                load(r, address)
                  .flatMap {
                    case Right(result) =>
                      Sync[F].pure(toProducerRecords(committable.record, committable.offset, result, outputTopic))
                    case Left(err) =>
                      Sync[F].pure(handleDataProviderError(toTilliJsonEventCommittable(committable), err, inputTopic, outputTopicFailure, TilliDataProvider))
                  }
              }
            }.parJoinUnbounded
            .through(fs2.kafka.KafkaProducer.pipe(kafkaProducer.producerSettings, producer))
            .map(_.passthrough)
            .through(commitBatchWithin(kafkaConsumerConfig.batchSize, kafkaConsumerConfig.batchDurationMs.milliseconds))
        )
    stream.compile.drain

  }

  def load[F[_] : Async : Sync](
    r: Resources[F],
    address: String,
  ): F[Either[Throwable, List[AnalyticsResultStatsV1]]] =
    loadByAddress(address, r.transactionCollection)
      .flatMap {
        case Left(err) => Sync[F].pure(Left(err))
        case Right(res) => Sync[F].delay(tally(address, res))
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
          //          .include("data.totalPrice") // TODO: Enable when we fix the price issue
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
  ): Either[IllegalStateException, List[AnalyticsResultStatsV1]] = {
    val tokens: Seq[Either[IllegalStateException, AnalyticsResult]] = docs
      .groupBy(r => s"${r.data.assetContractAddress}-${r.data.tokenId}")
      .values
      .toList
      .filter(_.exists(r => r.data.toAddress.map(_.toLowerCase) != r.data.fromAddress.map(_.toLowerCase)))
      .map(_.toList.sortBy(_.data.transactionTime))
      .sortBy(e => e.headOption.map(_.data.transactionTime))
      .flatMap { docs =>
        val firstRecord = docs.head
        val tokenId = firstRecord.data.tokenId
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
              case 1 =>
                val sign = g.head._1
                if (sign < 0) Right((g.head._1, None, transactions))
                else Right((g.head._1, g.head._2.map(start => Duration.between(start, Instant.now).toDays), transactions))
              case 2 =>
                val duration = for {
                  start <- g.head._2
                  end <- g(1)._2
                } yield Duration.between(start, end).toDays
                Right((g.map(_._1).sum, duration, transactions))
              case _ => Left(new IllegalStateException("invalid number of transactions in group of 2"))
            }
          }
          .map {
            case Right(res) =>
              for {
                tokenId <- firstRecord.data.tokenId.toRight(new IllegalStateException(s"Could not find any records for address $address"))
                assetContractAddress <- firstRecord.data.assetContractAddress.toRight(new IllegalStateException(s"Could not find any asset contract address for address $address"))
              } yield {
                AnalyticsResult(
                  address = address,
                  tokenId = tokenId,
                  assetContractAddress = assetContractAddress,
                  assetContractName = firstRecord.data.assetContractName,
                  assetContractType = firstRecord.assetContractType,
                  count = Option(res._1),
                  duration = res._2,
                  originatedFromNullAddress = firstRecord.data.fromAddress.contains(NullAddress),
                  transactions = Some(res._3),
                )
              }
            case Left(err) => Left(err)
          }
        transactions
      }

    val userAnalytics =
      tokens
        .sequence
        .map { g =>
          val currentHoldings = g.filter(_.count.exists(_>0))
          val tokenCount = currentHoldings.flatMap(_.count).sum
          val assetContractAddresses = currentHoldings.map(_.assetContractAddress).distinct
          val holdTimes = g.flatMap(_.duration)
          val holdMin = if (holdTimes.nonEmpty) Some(holdTimes.min) else None
          val holdMax = if (holdTimes.nonEmpty) Some(holdTimes.max) else None
          val holdAvg = if (holdTimes.nonEmpty) Option(holdTimes.sum.toDouble / holdTimes.size) else None
          val mints = g.count(_.originatedFromNullAddress)
          val transactionCount = g.size
          AnalyticsResultStatsV1(
            address = address,
            holdTimeAvg = holdAvg,
            holdTimeMax = holdMax.map(_.toInt),
            holdTimeMin = holdMin.map(_.toInt),
            mints = Some(mints),
            transactions = Some(transactionCount),
            tokens = Some(tokenCount),
            assetContracts = assetContractAddresses,
          )
        }
        .map(List(_))

    userAnalytics
  }

  def toProducerRecords[F[_]](
    record: ConsumerRecord[String, TilliAnalyticsAddressRequestEvent],
    offset: CommittableOffset[F],
    result: List[AnalyticsResultStatsV1],
    outputTopic: OutputTopic,
  )(implicit
    encoder: Encoder[AnalyticsResultStatsV1],
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    val producerRecords = result.map {
      ar =>
        val event = TilliJsonEvent(
          header = BlockchainClasses.Header(DataAnalyticsResultStatsV1Event, Some(record.value.header.trackingId)),
          data = ar.asJson,
        )
        ProducerRecord(outputTopic.name, ar.address, event)
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
