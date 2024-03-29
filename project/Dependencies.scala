import sbt._

object Dependencies {

  val catsVersion = "2.7.0"
  val catsEffectVersion = "3.3.12"
  val http4sVersion = "0.23.12"
  val fs2CoreVersion = "3.2.8"
  val fs2KafkaVersion = "2.5.0-M3"
//  val fs2KafkaVersion = "3.0.0-M7"
  val circeVersion = "0.14.1"

  val tapirVersion = "1.0.1"

  val core = Seq(
    "org.typelevel" %% "cats-core" % catsVersion,
    "org.typelevel" %% "cats-free" % catsVersion,
    "org.typelevel" %% "cats-effect" % catsEffectVersion,
    "co.fs2" %% "fs2-core" % fs2CoreVersion,

    "io.chrisdavenport" %% "mules" % "0.5.0"
  )

  val utils = Seq(
    "ch.qos.logback" % "logback-classic" % "1.2.11",

    "com.typesafe" % "config" % "1.4.2",
    "com.github.pureconfig" %% "pureconfig" % "0.17.1",
    "com.github.pureconfig" %% "pureconfig-cats-effect" % "0.17.1",
    "com.github.pureconfig" %% "pureconfig-enum" % "0.17.1",
  )

  val testDependencies = Seq(
    "org.scalatest" %% "scalatest" % "3.2.12",
  )

  val apiDependencies = Seq(
    "org.http4s" %% "http4s-blaze-client" % http4sVersion,
    "org.http4s" %% "http4s-blaze-server" % http4sVersion,
    "org.http4s" %% "http4s-circe" % http4sVersion,

    "org.systemfw" %% "upperbound" % "0.4.0",

    "com.softwaremill.sttp.tapir" %% "tapir-core" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % tapirVersion,

    "com.softwaremill.sttp.tapir" %% "tapir-openapi-circe-yaml" % "1.0.0-M9",
    "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-http4s" % "0.19.0-M4",
  )

  val dataDependencies = Seq(
    "com.github.fd4s" %% "fs2-kafka" % fs2KafkaVersion,
    "io.github.kirill5k" %% "mongo4cats-core" % "0.4.8",
    "io.github.kirill5k" %% "mongo4cats-circe" % "0.4.8",
  )

  val serdesDependencies = Seq(
    "io.circe" %% "circe-parser" % circeVersion,
    "io.circe" %% "circe-optics" % circeVersion,
  )

  val cloudDependencies = Seq(
    "com.google.cloud" % "google-cloud-storage" % "2.8.1",
  )
}
