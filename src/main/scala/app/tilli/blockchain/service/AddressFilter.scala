package app.tilli.blockchain.service

import app.tilli.blockchain.codec.BlockchainClasses
import app.tilli.blockchain.codec.BlockchainClasses._
import app.tilli.blockchain.codec.BlockchainCodec._
import app.tilli.blockchain.codec.BlockchainConfig.{DataTypeAddressRequest, DataTypeToVersion}
import app.tilli.collection.{LRUCache, LRUCacheMap}
import app.tilli.persistence.kafka.{KafkaConsumer, KafkaProducer}
import app.tilli.utils.{Logging, OutputTopic}
import cats.effect.{Async, Sync}
import fs2.kafka._
import io.circe.optics.JsonPath.root
import io.circe.syntax.EncoderOps

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.DurationLong

object AddressFilter extends Logging {

  private val cache = new LRUCacheMap[String, String](maxEntries = 20000)

  def addressFilterStream[F[_] : Async](
    r: Resources,
  )(implicit
    valueDeserializer: Deserializer[F, TilliJsonEvent],
    keySerializer: RecordSerializer[F, String],
    valueSerializer: RecordSerializer[F, TilliJsonEvent]
  ): F[Unit] = {
    val kafkaConsumerConfig = r.appConfig.kafkaConsumerConfiguration
    val kafkaProducerConfig = r.appConfig.kafkaProducerConfiguration
    val kafkaConsumer = new KafkaConsumer[String, TilliJsonEvent](kafkaConsumerConfig)
    val kafkaProducer = new KafkaProducer[String, TilliJsonEvent](kafkaProducerConfig)
    val inputTopic = r.appConfig.inputTopicAddressContractEvent
    val outputTopic = r.appConfig.outputTopicAddressRequest

    import fs2.kafka._
    val stream =
      kafkaProducer.producerStream.flatMap(producer =>
        kafkaConsumer
          .consumerStream
          .subscribeTo(inputTopic.name)
          .records
          .mapAsync(8) { committable =>
            val trackingId = committable.record.value.header.trackingId
            val addressRequests = filter(r.transactionEventSource, committable.record, cache)
            Sync[F].pure(toProducerRecords(committable.record, committable.offset, addressRequests, outputTopic, trackingId))
          }
          .through(KafkaProducer.pipe(kafkaProducer.producerSettings, producer))
          .map(_.passthrough)
          .through(commitBatchWithin(kafkaConsumerConfig.batchSize, kafkaConsumerConfig.batchDurationMs.milliseconds))
      )
    stream.compile.drain
  }

  def filter[F[_] : Sync : Async](
    source: TransactionEventSource[F],
    record: ConsumerRecord[String, TilliJsonEvent],
    cache: LRUCache[String, String],
  ): List[AddressRequest] = {
    val toAddress = root.toAddress.string.getOption(record.value.data)
    val fromAddress = root.fromAddress.string.getOption(record.value.data)
    val blockChain = root.chain.string.getOption(record.value.data)
    List(
      checkAndInsertIntoCache(fromAddress, cache).map(a => AddressRequest(a, blockChain)),
      checkAndInsertIntoCache(toAddress, cache).map(a => AddressRequest(a, blockChain))
    ).flatten
  }

  def checkAndInsertIntoCache(
    address: Option[String],
    cache: LRUCache[String, String],
  ): Option[String] =
    address.flatMap { a =>
      if (cache.getOption(a).nonEmpty) None
      else {
        cache.put(a, a)
        address
      }
    }

  def toProducerRecords[F[_]](
    record: ConsumerRecord[String, TilliJsonEvent],
    offset: CommittableOffset[F],
    addressRequests: List[AddressRequest],
    outputTopic: OutputTopic,
    trackingId: UUID,
  ): ProducerRecords[CommittableOffset[F], String, TilliJsonEvent] = {
    val sourcedTime = Instant.now.toEpochMilli
    val requests = addressRequests.map { ar =>
      val tilliJsonEvent = TilliJsonEvent(
        BlockchainClasses.Header(
          trackingId = trackingId,
          eventTimestamp = sourcedTime,
          eventId = UUID.randomUUID(),
          origin = record.value.header.origin,
          dataType = Some(DataTypeAddressRequest),
          version = DataTypeToVersion.get(DataTypeAddressRequest)
        ),
        data = ar.asJson,
      )
      ProducerRecord(outputTopic.name, ar.address, tilliJsonEvent)
    }
    ProducerRecords(
      requests,
      offset
    )
  }
}
