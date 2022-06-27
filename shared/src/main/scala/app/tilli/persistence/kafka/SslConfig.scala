package app.tilli.persistence.kafka

import app.tilli.integration.gcp.GcsFileLoader
import app.tilli.logging.Logging

trait SslConfig extends Logging {

  def processSslConfig(sslConfig: Map[String, String]): Map[String, String] = {
    sslConfig
      .map(e =>
        if (e._2.contains("gs://")) {
          GcsFileLoader.loadFromGcsToLocalFile(e._2) match {
            case Right(file) =>
              val temp = file.getAbsolutePath
              log.info(s"Converted ${e._2} to $temp for property ${e._1}")
              e._1 -> temp
            case Left(err) =>
              log.error("Error occurred while loading SSL Config", err)
              e._1 -> e._2
          }
        } else e._1 -> e._2
      )
  }

}
