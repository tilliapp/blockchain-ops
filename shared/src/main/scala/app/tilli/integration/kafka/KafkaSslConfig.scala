package app.tilli.integration.kafka

object KafkaSslConfig {

  val sslConfig = Map(
    "security.protocol" -> "SSL",
    "ssl.truststore.location" -> "gs://tilli-prod-kafka-secrets/client.truststore-14236929421944531078.jks",
    "ssl.truststore.password" -> "a1cd60a4e89d436c913dc996bf40d6ca",
    "ssl.keystore.type" -> "PKCS12",
    "ssl.keystore.location" -> "gs://tilli-prod-kafka-secrets/client.keystore-12010477626053255492.p12",
    "ssl.keystore.password" -> "fd13542854dd47d7bbfb774b32caf261",
    "ssl.key.password" -> "fd13542854dd47d7bbfb774b32caf261",
    "ssl.endpoint.identification.algorithm" -> "",
  )

}
