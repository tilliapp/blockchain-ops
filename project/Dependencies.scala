import sbt._

object Dependencies {

  val catsCoreVersion = "2.7.0"

  val scalaTestVersion = "3.2.11"

  val flinkVersion = "1.15.0"
  val flinkScalaBindingsVersion = "1.15.2"

  val core = Seq(
    "ch.qos.logback" % "logback-classic" % "1.2.11",
  )

  val flinkDependencies = Seq(
    //// Scala 2.13 Arrisk support:
    "com.ariskk" % "flink4s_2.13" % flinkScalaBindingsVersion % "provided",
    "org.apache.flink" % "flink-clients" % flinkVersion,
  )

  val testDependencies = Seq(
    "org.scalatest" %% "scalatest" % "3.2.11",
  )

}
