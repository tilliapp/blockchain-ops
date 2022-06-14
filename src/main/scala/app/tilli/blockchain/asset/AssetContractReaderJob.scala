//package app.tilli.blockchain.asset
//
//// original api import
//import org.apache.flink.streaming.api.scala._
//
//// flink-scala-api imports
//
////import io.findify.flink.api._
////import io.findify.flinkadt.api._
//import org.apache.flink.api.java.functions.KeySelector
//
//class AssetContractReaderJob {
//
//  val env = StreamExecutionEnvironment.getExecutionEnvironment
//
//  val inputStream: DataStream[(Int, Int, Int)] = env.fromElements(
//    (1, 2, 2), (2, 3, 1), (2, 2, 4), (1, 5, 3)
//  )
//
//  val resultStream: DataStream[(Int, Int, Int)] = inputStream
//    .keyBy(new KeySelector[(Int, Int, Int), Int] {
//      override def getKey(value: (Int, Int, Int)): Int = value._1
//    }) // key on first field of the tuple
//    .sum(1) // sum the second field of the tuple
//
//  resultStream.print()
//
//  // execute application
//  env.execute("Rolling Sum Example")
//
//}
