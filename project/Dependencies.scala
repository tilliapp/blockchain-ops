import sbt._

object Dependencies {

  val catsCoreVersion = "2.7.0"

  val scalaTestVersion = "3.2.11"

  val flinkVersion = "1.15.0"
  val flinkScalaBindingsVersion = "1.15.2"

  val core = Seq(
    "ch.qos.logback" % "logback-classic" % "1.2.11",
//    "org.typelevel" %% "cats-core" % catsCoreVersion
  )

  val flinkDependencies = Seq(
//    //// Scala 2.13 Arrisk support:
//    "com.ariskk" % "flink4s_2.13" % flinkScalaBindingsVersion,
//    ////    "org.apache.flink" %% "flink-scala" % "1.15.0",
//    "org.apache.flink" % "flink-clients" % flinkVersion,

    // Scala 2.13 support:
    //    "io.findify" %% "flink-scala-api" % "1.15-2" % "provided",
    //    "io.findify" %% "flink-adt" % "0.6.1" % "provided",
    //    "org.apache.flink" % "flink-scala_2.12" % "1.15.0" % "provided",
    //    "org.apache.flink" % "flink-streaming-scala_2.12" % "1.15.0" % "provided",
    //    "org.apache.flink" % "flink-clients" % "1.15.0" % "provided",

    // Mixed Scala 2.12 support:
    //        "io.findify" %% "flink-scala-api" % "1.15-2",
    //        "io.findify" %% "flink-adt" % "0.6.1",
    //        "org.apache.flink" %% "flink-scala",
    //        "org.apache.flink" %% "flink-streaming-scala" % "1.15.0",
    //        "org.apache.flink" % "flink-clients" % "1.15.0",

//     Scala 2.12 support:
//        "org.scala-lang" % "scala-library" % "2.13.8"
            "org.apache.flink" %% "flink-scala" % "1.15.0",
            "org.apache.flink" %% "flink-streaming-scala" % "1.15.0",
            "org.apache.flink" % "flink-clients" % "1.15.0",
  )

  val testDependencies = Seq(
    "org.scalatest" %% "scalatest" % scalaTestVersion,
  )

}
