package app.tilli.blockchain.api.internal

import app.tilli.api.codec.{ApiCodec, TilliSttpSchema}
import app.tilli.api.response.Response.ErrorResponse
import app.tilli.api.utils.ApiSerdes.Serializer
import app.tilli.blockchain.codec.BlockchainClasses.{AssetContract, AssetContractRequest, HttpClientError}
import app.tilli.blockchain.codec.BlockchainCodec._
import app.tilli.blockchain.service.blockchainreader.Resources
import app.tilli.persistence.kafka.KafkaProducer
import app.tilli.utils.InputTopic
import cats.data.EitherT
import cats.effect.{Async, IO}
import fs2.kafka.{ProducerRecord, ProducerRecords, ProducerResult, RecordSerializer}
import org.http4s.HttpRoutes
import sttp.tapir.Endpoint
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.http4s.Http4sServerInterpreter

object SubmitAssetContractRequest extends ApiCodec with TilliSttpSchema {

  val endpoint: Endpoint[Unit, AssetContract, ErrorResponse, AssetContractRequest, Any] =
    sttp.tapir.endpoint
      .post
      .in("asset_contract")
      .in(jsonBody[AssetContract])
      .out(Serializer.jsonBody[AssetContractRequest])
      .errorOut(Serializer.jsonBody[ErrorResponse])
      .name("Submit Asset Contract Request")

  def service(implicit
    resources: Resources,
  ): HttpRoutes[IO] = {
    Http4sServerInterpreter[IO]().toRoutes(endpoint.serverLogic(function))
  }

  def function(
    assetContract: AssetContract,
  )(implicit
    resources: Resources,
  ): IO[Either[ErrorResponse, AssetContractRequest]] = {
    val source = resources.assetContractSource
    val kafkaProducerConfig = resources.appConfig.kafkaProducerConfiguration
    val kafkaProducer = new KafkaProducer[String, AssetContractRequest](kafkaProducerConfig, resources.sslConfig)
    val chain =
      for {
        enrichedAssetContract <- EitherT(
          source
            .getAssetContract(assetContract.address, rateLimiter = None)
            .map(res => res.flatMap(j => j.as[AssetContract]))
        )
        assetContractRequest = AssetContractRequest(enrichedAssetContract)
      } yield assetContractRequest

    chain
      .flatMap(assetContractRequest =>
        EitherT(
          writeToKafka(assetContractRequest, kafkaProducer, resources.appConfig.inputTopicAssetContractRequest)
            .compile
            .drain
            .attempt
            .map(_.map(_ => assetContractRequest))
        )
      )
      .leftMap {
        case t: HttpClientError => ErrorResponse(t)
        case t: Throwable => ErrorResponse(Option(t.getMessage).getOrElse("Unknown error"), None)
      }
      .value
  }

  def writeToKafka(
    assetContractRequest: AssetContractRequest,
    kafkaProducer: KafkaProducer[String, AssetContractRequest],
    topic: InputTopic,
  )(implicit
    keySerializer: RecordSerializer[IO, String],
    valueSerializer: RecordSerializer[IO, AssetContractRequest],
    async: Async[IO],
  ): fs2.Stream[IO, IO[ProducerResult[Unit, String, AssetContractRequest]]] =
    kafkaProducer
      .producerStream
      .flatMap(producer =>
        fs2.Stream.eval(
          producer.produce(toProducerRecord(assetContractRequest, topic))
        )
      )

  def toProducerRecord[F[_]](
    assetContractRequest: AssetContractRequest,
    topic: InputTopic,
  ): ProducerRecords[Unit, String, AssetContractRequest] =
    ProducerRecords.one(
      record = ProducerRecord(
        topic = topic.name,
        key = assetContractRequest.data.address,
        value = assetContractRequest,
      )
    )

}
