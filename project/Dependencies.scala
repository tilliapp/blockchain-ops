import sbt._

object Dependencies {

  val catsVersion = "2.7.0"
  val catsEffectVersion = "3.3.12"
  val http4sVersion = "0.23.12"
  val fs2CoreVersion = "3.2.8"
  val fs2Version = "2.5.0-M3"
  val circeVersion = "0.14.1"

  val tapirVersion = "1.0.0"

  val core = Seq(
    "org.typelevel" %% "cats-core" % catsVersion,
    "org.typelevel" %% "cats-free" % catsVersion,
    "org.typelevel" %% "cats-effect" % catsEffectVersion,
    "co.fs2" %% "fs2-core" % fs2CoreVersion,

    "ch.qos.logback" % "logback-classic" % "1.2.11",
  )

  val testDependencies = Seq(
    "org.scalatest" %% "scalatest" % "3.2.12",
  )

  val apiDependencies = Seq(
    "org.http4s" %% "http4s-blaze-client" % http4sVersion,
    "org.http4s" %% "http4s-blaze-server" % http4sVersion,
    "org.http4s" %% "http4s-circe" % http4sVersion,

    "com.softwaremill.sttp.tapir" %% "tapir-core" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % tapirVersion,
  )

  val streamingDependencies = Seq(
    "com.github.fd4s" %% "fs2-kafka" % fs2Version,
  )

  val serdesDependencies = Seq(
    "io.circe" %% "circe-parser" % circeVersion,
    "io.circe" %% "circe-optics" % circeVersion,
  )
}
