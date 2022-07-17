package app.tilli.blockchain.api.internal

import app.tilli.api.codec.{ApiCodec, TilliSttpSchema}
import app.tilli.api.response.Response.ErrorResponse
import app.tilli.api.utils.ApiSerdes.Serializer
import app.tilli.blockchain.codec.BlockchainClasses.{AssetContract, AssetContractRequest, HttpClientError, TilliAssetContractRequestEvent}
import app.tilli.blockchain.codec.BlockchainCodec._
import app.tilli.blockchain.service.blockchainreader.Resources
import app.tilli.persistence.kafka.KafkaProducer
import app.tilli.utils.{InputTopic, OutputTopic}
import cats.data.EitherT
import cats.effect.{Async, IO}
import fs2.kafka.{ProducerRecord, ProducerRecords, ProducerResult, RecordSerializer}
import org.http4s.HttpRoutes
import sttp.tapir.Endpoint
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.http4s.Http4sServerInterpreter

object SubmitAssetContractRequest extends ApiCodec with TilliSttpSchema {

  val endpoint: Endpoint[Unit, AssetContractRequest, ErrorResponse, TilliAssetContractRequestEvent, Any] =
    sttp.tapir.endpoint
      .post
      .in("asset_contract")
      .in(jsonBody[AssetContractRequest])
      .out(Serializer.jsonBody[TilliAssetContractRequestEvent])
      .errorOut(Serializer.jsonBody[ErrorResponse])
      .name("Submit Asset Contract Request")

  def service(implicit
    resources: Resources,
  ): HttpRoutes[IO] = {
    Http4sServerInterpreter[IO]().toRoutes(endpoint.serverLogic(function))
  }

  def function(
    assetContractRequest: AssetContractRequest,
  )(implicit
    resources: Resources,
  ): IO[Either[ErrorResponse, TilliAssetContractRequestEvent]] = {
    val startSync = Some(assetContractRequest.startSync.contains(true))
    val source = resources.assetContractSource
    val kafkaProducerConfig = resources.appConfig.kafkaProducerConfiguration
    val kafkaProducer = new KafkaProducer[String, TilliAssetContractRequestEvent](kafkaProducerConfig, resources.sslConfig)
    val chain =
      for {
        enrichedAssetContract <- EitherT(
          source
            .getAssetContract(assetContractRequest.assetContract.address, rateLimiter = None)
            .map(res => res.flatMap(j => j.as[AssetContract]))
        )
      } yield AssetContractRequest(enrichedAssetContract, Some(1), startSync)

    chain
      .flatMap { assetContractRequest =>
        val event = TilliAssetContractRequestEvent(assetContractRequest)
        EitherT(
          writeToKafka(event, kafkaProducer, resources.appConfig.outputTopicAssetContractRequest)
            .compile
            .drain
            .attempt
            .map(_.map(_ => event))
        )
      }
      .leftMap {
        case t: HttpClientError => ErrorResponse(t)
        case t: Throwable => ErrorResponse(Option(t.getMessage).getOrElse("Unknown error"), None)
      }
      .value
  }

  def writeToKafka(
    assetContractRequestEvent: TilliAssetContractRequestEvent,
    kafkaProducer: KafkaProducer[String, TilliAssetContractRequestEvent],
    topic: OutputTopic,
  )(implicit
    keySerializer: RecordSerializer[IO, String],
    valueSerializer: RecordSerializer[IO, TilliAssetContractRequestEvent],
    async: Async[IO],
  ): fs2.Stream[IO, IO[ProducerResult[Unit, String, TilliAssetContractRequestEvent]]] =
    kafkaProducer
      .producerStream
      .flatMap(producer =>
        fs2.Stream.eval(
          producer.produce(toProducerRecord(assetContractRequestEvent, topic))
        )
      )

  def toProducerRecord[F[_]](
    assetContractRequestEvent: TilliAssetContractRequestEvent,
    topic: OutputTopic,
  ): ProducerRecords[Unit, String, TilliAssetContractRequestEvent] =
    ProducerRecords.one(
      record = ProducerRecord(
        topic = topic.name,
        key = assetContractRequestEvent.data.assetContract.address,
        value = assetContractRequestEvent,
      )
    )

}
