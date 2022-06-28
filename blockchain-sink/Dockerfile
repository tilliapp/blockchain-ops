FROM openjdk:latest
ADD blockchain-sink/target/scala-2.13/run.jar /run.jar
ENV ENVIRONMENT=production
ENTRYPOINT ["java", "-Xmx1024m", "-cp", "/run.jar", "app.tilli.blockchain.service.blockchainsink.BlockchainSinkService"]