package app.tilli.blockchain.asset

object AssetContractReader {

  def main(args: Array[String]): Unit = {
    val job = new AssetContractReaderJob(
      "127.0.0.1:9092",
      "asset_contract_request"
    )

    job.run()
  }

}
