package app.tilli.integration.gcp

import app.tilli.logging.Logging
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.{Blob, BlobId, StorageOptions}

import java.io.{File, FileOutputStream}
import scala.util.{Random, Try}

object GcsFileLoader extends Logging {

  def loadFromGcsToLocalFile(
    gcsPath: String,
    fileOutputPath: Option[String] = None,
  ): Either[Throwable, File] = {
    Try {
      val blobId = BlobId.fromGsUtilUri(gcsPath)

      val storage = StorageOptions.newBuilder()
        .setCredentials(GoogleCredentials.getApplicationDefault())
        .build()
        .getService

      val blob = storage.get(blobId)
      val readChannel = blob.reader()
      val fileName = new String(Random.alphanumeric.take(20).toArray)
      val finalFileOutputPath = fileOutputPath.getOrElse(s"/tmp/$fileName")
      val fileOutputStream = new FileOutputStream(finalFileOutputPath)
      fileOutputStream.getChannel.transferFrom(readChannel, 0, Long.MaxValue)
      new File(finalFileOutputPath)
    }.toEither
  }

}
