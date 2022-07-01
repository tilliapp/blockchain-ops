package app.tilli.utils

import java.time.Instant
import scala.util.Try

object DateUtils {

  def tsToEpochMilli(ts: Option[String]): Option[Long] =
    ts.map(ts => if (!ts.toLowerCase.endsWith("z")) s"${ts}Z" else ts)
      .flatMap(ts => Try(Instant.parse(ts)).toOption).map(_.toEpochMilli)

  def tsToInstant(ts: Option[String]): Option[Instant] =
    ts.map(ts => if (!ts.toLowerCase.endsWith("z")) s"${ts}Z" else ts)
      .flatMap(ts => Try(Instant.parse(ts)).toOption)

}
