package app.tilli.blockchain.asset

import cats.Semigroup
import com.ariskk.flink4s._
import org.apache.flink.api.common.typeinfo.TypeInformation

case class ThreeInts(a: Int, b: Int, c: Int)

object TestJob {
  implicit val typeInfoInt = TypeInformation.of(classOf[Int])
  implicit val typeInfoThreeInts = TypeInformation.of(classOf[ThreeInts])
  implicit val threeIntsSemiGroup: Semigroup[ThreeInts] = (x: ThreeInts, y: ThreeInts) => ThreeInts(x.a, y.b, y.c)
}

class TestJob {
  import TestJob._

  val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment

  val inputStream: DataStream[ThreeInts] = env.fromCollection(
    List(
      ThreeInts(1, 2, 2), ThreeInts(2, 3, 1), ThreeInts(2, 2, 4), ThreeInts(1, 5, 3)
    )
  )

  val resultStream: DataStream[ThreeInts] = inputStream
    .keyBy[Int](_.a) // key on first field of the tuple
    .combine(threeIntsSemiGroup)

  resultStream.print()

  // execute application
  env.execute

}
