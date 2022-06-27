FROM openjdk:latest
ADD target/scala-2.13/run.jar /run.jar
ENTRYPOINT ["java", "-Xmx1536m", "-cp", "/run.jar", "app.tilli.blockchain.service.blockchainreader.BlockchainReaderService"]