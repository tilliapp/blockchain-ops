import sbt._

object Dependencies {

  val core = Seq(
    "ch.qos.logback" % "logback-classic" % "1.2.11",
  )

  val flinkDependencies = Seq(
    // Scala 2.13 support:
    "io.findify" %% "flink-scala-api" % "1.15-2",
    "org.apache.flink" % "flink-scala_2.12" % "1.15.0",
    "org.apache.flink" % "flink-streaming-scala_2.12" % "1.15.0",
    "org.apache.flink" % "flink-clients" % "1.15.0",

    // Scala 2.12 support:
    //    "org.scala-lang" % "scala-library" % "2.13.8"
    //    "org.apache.flink" %% "flink-scala" % "1.15.0",
    //    "org.apache.flink" %% "flink-streaming-scala" % "1.15.0",
    //    "org.apache.flink" % "flink-clients" % "1.15.0",
  )

  val testDependencies = Seq(
    "org.scalatest" %% "scalatest" % "3.2.11",
  )

  val apiDependencies = Seq(
  )

  val dataDependencies = Seq(
  )

  val serdesDependencies = Seq(
  )

  val web3Dependencies = Seq(
  )

}
