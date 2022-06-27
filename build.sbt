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
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case x => MergeStrategy.first
  }
)

lazy val blockchainReaderService = (project in file("."))
  .settings(
    name := "blockchain-reader-service",
    sharedSettings,
    libraryDependencies ++= Dependencies.core,
    libraryDependencies ++= Dependencies.utils,
    libraryDependencies ++= Dependencies.testDependencies,
    libraryDependencies ++= Dependencies.apiDependencies,
    libraryDependencies ++= Dependencies.dataDependencies,
    libraryDependencies ++= Dependencies.serdesDependencies,
    mainClass in assembly := Some("app.tilli.blockchain.service.blockchainreader.BlockchainReaderService"),
    assemblyJarName in assembly := "blockchain-reader-service.jar"
  )

lazy val mongoDbService = (project in file("."))
  .settings(
    name := "mongo-db-service",
    sharedSettings,
    libraryDependencies ++= Dependencies.core,
    libraryDependencies ++= Dependencies.utils,
    libraryDependencies ++= Dependencies.testDependencies,
    libraryDependencies ++= Dependencies.apiDependencies,
    libraryDependencies ++= Dependencies.dataDependencies,
    libraryDependencies ++= Dependencies.serdesDependencies,
    mainClass in assembly := Some("app.tilli.blockchain.service.mongodbsink.MongoDbService"),
    assemblyJarName in assembly := "mongo-db-service.jar"
  )