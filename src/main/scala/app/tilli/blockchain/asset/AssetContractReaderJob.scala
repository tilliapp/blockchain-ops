package app.tilli.blockchain.asset

// original api import

import org.apache.flink.streaming.api.scala._

class AssetContractReaderJob {

  val env = StreamExecutionEnvironment.getExecutionEnvironment

  val inputStream: DataStream[(Int, Int, Int)] = env.fromCollection(
    List(
      (1, 2, 2), (2, 3, 1), (2, 2, 4), (1, 5, 3)
    ))

  val resultStream: DataStream[(Int, Int, Int)] = inputStream
    .keyBy({ a => a._1 })
    .sum(1) // sum the second field of the tuple

  resultStream.print()

  // execute application
  env.execute("Rolling Sum Example")

}
