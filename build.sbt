import sbt.Keys._

val sharedSettings: Seq[Def.Setting[_]] = Seq(
  organization := "app.tilli",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.13.8",
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-feature",
    "-language:implicitConversions",
    "-language:higherKinds",
    "-Ymacro-annotations",
    "-Ywarn-dead-code",
    "-Xlint:unused",
    "-Wdead-code",
  ),
  fork := true,
  publishArtifact in Test := true,
  test in assembly := {},
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", "maven", "org.webjars", "swagger-ui", "pom.properties") => MergeStrategy.singleOrError
    case PathList("META-INF", xs@_*) => MergeStrategy.discard
    case x => MergeStrategy.first
  }
)

lazy val root = (project in file("."))
  .aggregate(
    shared,
    blockchainContractReader,
    blockchainTransactionReader,
    blockchainSink,
    blockchainAnalytics,
  )

lazy val shared = (project in file("shared"))
  .settings(
    name := "shared",
    sharedSettings,
    libraryDependencies ++= Dependencies.core,
    libraryDependencies ++= Dependencies.utils,
    libraryDependencies ++= Dependencies.testDependencies,
    libraryDependencies ++= Dependencies.apiDependencies,
    libraryDependencies ++= Dependencies.dataDependencies,
    libraryDependencies ++= Dependencies.serdesDependencies,
    libraryDependencies ++= Dependencies.cloudDependencies,
  )

lazy val blockchainContractReader = (project in file("blockchain-contract-reader"))
  .settings(
    name := "blockchainContractReader",
    sharedSettings,
    libraryDependencies ++= Dependencies.core,
    libraryDependencies ++= Dependencies.utils,
    libraryDependencies ++= Dependencies.testDependencies,
    libraryDependencies ++= Dependencies.apiDependencies,
    libraryDependencies ++= Dependencies.dataDependencies,
    libraryDependencies ++= Dependencies.serdesDependencies,
    mainClass in assembly := Some("app.tilli.blockchain.service.blockchainreader.BlockchainContractReaderService"),
    assemblyJarName in assembly := "run.jar"
  ).dependsOn(shared)

lazy val blockchainSink = (project in file("blockchain-sink"))
  .settings(
    name := "blockchainSink",
    sharedSettings,
    libraryDependencies ++= Dependencies.core,
    libraryDependencies ++= Dependencies.utils,
    libraryDependencies ++= Dependencies.testDependencies,
    libraryDependencies ++= Dependencies.apiDependencies,
    libraryDependencies ++= Dependencies.dataDependencies,
    libraryDependencies ++= Dependencies.serdesDependencies,
    mainClass in assembly := Some("app.tilli.blockchain.service.blockchainsink.BlockchainSinkService"),
    assemblyJarName in assembly := "run.jar"
  ).dependsOn(shared)

lazy val blockchainTransactionReader = (project in file("blockchain-transaction-reader"))
  .settings(
    name := "blockchainTransactionReader",
    sharedSettings,
    libraryDependencies ++= Dependencies.core,
    libraryDependencies ++= Dependencies.utils,
    libraryDependencies ++= Dependencies.testDependencies,
    libraryDependencies ++= Dependencies.apiDependencies,
    libraryDependencies ++= Dependencies.dataDependencies,
    libraryDependencies ++= Dependencies.serdesDependencies,
    mainClass in assembly := Some("app.tilli.blockchain.service.blockchainreader.BlockchainTransactionReaderService"),
    assemblyJarName in assembly := "run.jar"
  ).dependsOn(shared)

lazy val blockchainAnalytics = (project in file("blockchain-analytics"))
  .settings(
    name := "blockchainAnalytics",
    sharedSettings,
    libraryDependencies ++= Dependencies.core,
    libraryDependencies ++= Dependencies.utils,
    libraryDependencies ++= Dependencies.testDependencies,
    libraryDependencies ++= Dependencies.apiDependencies,
    libraryDependencies ++= Dependencies.dataDependencies,
    libraryDependencies ++= Dependencies.serdesDependencies,
    mainClass in assembly := Some("app.tilli.blockchain.service.blockchainreader.BlockchainTransactionReaderService"),
    assemblyJarName in assembly := "run.jar"
  ).dependsOn(shared)