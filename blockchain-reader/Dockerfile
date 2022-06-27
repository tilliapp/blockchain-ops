FROM openjdk:latest
ADD blockchain-reader/target/scala-2.13/run.jar /run.jar
ENV ENVIRONMENT=production
ENTRYPOINT ["java", "-Xmx1536m", "-cp", "/run.jar", "app.tilli.blockchain.service.blockchainreader.BlockchainReaderService"]