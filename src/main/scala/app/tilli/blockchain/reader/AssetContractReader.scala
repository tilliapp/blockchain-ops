package app.tilli.blockchain.reader

import app.tilli.BlazeServer
import app.tilli.api.utils.{BlazeHttpClient, HttpClientConfig, HttpClientErrorTrait}
import app.tilli.blockchain.codec.BlockchainClasses.{DataProvider, Header, Origin, TilliJsonEvent}
import app.tilli.blockchain.codec.BlockchainCodec._
import app.tilli.blockchain.codec.BlockchainConfig.{DataTypeAssetContract, DataTypeToVersion}
import app.tilli.blockchain.config.AppConfig
import app.tilli.blockchain.config.AppConfig.readerAppConfig
import app.tilli.persistence.kafka.{KafkaConsumer, KafkaProducer, KafkaProducerConfiguration}
import app.tilli.utils.{ApplicationConfig, InputTopic, OutputTopic}
import cats.effect.{Async, Concurrent, ExitCode, IO, IOApp}
import fs2.kafka.{CommittableOffset, ConsumerRecord, ProducerRecord, ProducerRecords}
import io.circe.Json
import io.circe.optics.JsonPath.root
import org.http4s.client.Client
import upperbound.Limiter

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.DurationLong
import scala.util.Try

case class Resources(
  appConfig: AppConfig,
  httpClient: Client[IO],
  openSeaRateLimiter: Limiter[IO],
)

object AssetContractReader extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    implicit val async = Async[IO]
    val concurrent = Concurrent[IO]

    val resources = for {
      appConfig <- ApplicationConfig()
      httpClient <- BlazeHttpClient.clientWithRetry(appConfig.httpClientConfig)
      openSeaRateLimiter <- Limiter.start[IO](
        minInterval = appConfig.rateLimitOpenSea.minIntervalMs.milliseconds,
        maxConcurrent = appConfig.rateLimitOpenSea.maxConcurrent,
        maxQueued = appConfig.rateLimitOpenSea.maxQueued,
      )
    } yield Resources(appConfig, httpClient, openSeaRateLimiter)

    def httpServer(implicit r: Resources): IO[ExitCode] =
      BlazeServer
        .serverWithHealthCheck()
        .serve
        .compile
        .drain
        .as(ExitCode.Success)

    def kafkaStream(implicit r: Resources): IO[ExitCode] = {
      val kafkaConsumerConfig = r.appConfig.kafkaConsumerConfiguration
      val kafkaProducerConfig = r.appConfig.kafkaProducerConfiguration
      val kafkaConsumer = new KafkaConsumer[Option[UUID], Json](kafkaConsumerConfig)
      val kafkaProducer = new KafkaProducer[String, TilliJsonEvent](kafkaProducerConfig)
      val outputTopic = r.appConfig.outputTopicAssetContractRequest

      val openSeaApi = new OpenSeaApi[IO](r.httpClient, concurrent)

      import fs2.kafka._
      val stream =
        kafkaProducer.producerStream.flatMap(producer =>
          kafkaConsumer
            .consumerStream
            .subscribeTo(r.appConfig.inputTopicAssetContractRequest.name)
            .records
            //        .evalTap(r => IO(println(r.record.value)))
            .mapAsync(24) { committable =>
              val trackingId = root.trackingId.string.getOption(committable.record.value).flatMap(s => Try(UUID.fromString(s)).toOption).getOrElse(UUID.randomUUID())
              processRecord(openSeaApi, committable.record, r.openSeaRateLimiter)
                .map {
                  case Right(json) => toProducerRecord(committable.offset, json, outputTopic, trackingId, openSeaApi)
                  case Left(errorTrait) => toProducerRecord(committable.offset, Json.Null, outputTopic, trackingId, openSeaApi)
                }
            }
            .through(KafkaProducer.pipe(kafkaProducer.producerSettings, producer))
            .map(_.passthrough)
            .through(commitBatchWithin(kafkaConsumerConfig.batchSize, kafkaConsumerConfig.batchDurationMs.milliseconds))
        )
      stream.compile.drain.as(ExitCode.Success)
    }

    resources.use { implicit r =>
      httpServer &> kafkaStream
    }

  }

  def processRecord(
    openSeaApi: OpenSeaApi[IO],
    record: ConsumerRecord[Option[UUID], Json],
    rateLimiter: Limiter[IO],
  ): IO[Either[HttpClientErrorTrait, Json]] =
    IO(println(s"Processing record: $record")) *>
      openSeaApi.getAssetContract(
        record.key.getOrElse(UUID.randomUUID()),
        root.assetContractAddress.string.getOption(record.value).get,
        rateLimiter
      ).flatTap(e => IO(println(e)))


  def toProducerRecord(
    offset: CommittableOffset[IO],
    record: Json,
    outputTopic: OutputTopic,
    trackingId: UUID,
    dataProvider: DataProvider,
  ): ProducerRecords[CommittableOffset[IO], String, TilliJsonEvent] = {
    val key = root.address.string.getOption(record).orNull
    val sourced = root.sourced.long.getOption(record).getOrElse(Instant.now().toEpochMilli)
    val tilliJsonEvent = TilliJsonEvent(
      Header(
        trackingId = trackingId,
        eventTimestamp = Instant.now().toEpochMilli,
        eventId = UUID.randomUUID(),
        origin = List(
          Origin(
            source = Some(dataProvider.source),
            provider = Some(dataProvider.provider),
            sourcedTimestamp = sourced,
          )
        ),
        dataType = Some(DataTypeAssetContract),
        version = DataTypeToVersion.get(DataTypeAssetContract)
      ),
      data = record,
    )
    val producerRecord = ProducerRecord(outputTopic.name, key, tilliJsonEvent)
    ProducerRecords.one(producerRecord, offset)
  }

}
