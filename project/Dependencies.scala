import sbt._

object Dependencies {

  val catsVersion = "2.7.0"
  val catsEffectVersion= "3.3.5"
  val http4sVersion = "0.23.10"
  val circeVersion = "0.14.1"

  val core = Seq(
    "org.typelevel" %% "cats-core" % catsVersion,
    "org.typelevel" %% "cats-free" % catsVersion,
    "org.typelevel" %% "cats-effect" % catsEffectVersion,
    "co.fs2" %% "fs2-core" % "3.2.8",

    "ch.qos.logback" % "logback-classic" % "1.2.11",
  )

  val testDependencies = Seq(
    "org.scalatest" %% "scalatest" % "3.2.12",
  )

  val apiDependencies = Seq(
    "org.http4s" %% "http4s-blaze-client" % http4sVersion,
//    "org.http4s" %% "http4s-blaze-server" % http4sVersion,
    "org.http4s" %% "http4s-circe" % http4sVersion,
  )

  val serdesDependencies = Seq(
    "io.circe" %% "circe-parser" % circeVersion,
    "io.circe" %% "circe-optics" % circeVersion,
  )
}
