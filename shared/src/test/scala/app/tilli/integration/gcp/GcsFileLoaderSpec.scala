package app.tilli.integration.gcp

import app.tilli.BaseSpec

class GcsFileLoaderSpec extends BaseSpec {

  "GcsFileLoader" must {

    "load file from cloud into temp file" in {
      val Right(file) = GcsFileLoader.load(
        gcsPath = "gs://tilli-prod-kafka-secrets/client.keystore-12010477626053255492.p12",
      )
      file.isFile mustBe true
    }

  }

}
