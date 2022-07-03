FROM openjdk:latest
ADD blockchain-contract-reader/target/scala-2.13/run.jar /run.jar
ENV ENVIRONMENT=production
ENTRYPOINT ["java", "-Xmx4000m", "-cp", "/run.jar", "app.tilli.blockchain.service.blockchainreader.BlockchainContractReaderService"]