package app.tilli.serializer

import cats.free.Trampoline
import io.circe.{Json, ParsingFailure, Printer, parser}
import cats.syntax.traverse._

object KeyConverter {

  val noSpaces: Printer = Printer.noSpaces.copy(
    reuseWriters = true,
  )

  import io.circe.JsonObject

  /**
   * Helper method that transforms a single layer.
   */
  def transformObjectKeys(obj: JsonObject, f: String => String): JsonObject =
    JsonObject.fromIterable(
      obj.toList.map {
        case (k, v) => f(k) -> v
      }
    )


  def transformKeys(json: Json, f: String => String): Trampoline[Json] =
    json.arrayOrObject(
      Trampoline.done(json),
      _.traverse(j => Trampoline.defer(transformKeys(j, f))).map(Json.fromValues),
      transformObjectKeys(_, f)
        .traverse(obj =>
          Trampoline.defer(transformKeys(obj, f)))
        .map(Json.fromJsonObject)
    )

  def sc2cc(in: String): String = "_([a-z\\d])".r.replaceAllIn(in, _.group(1).toUpperCase)

  def snakeCaseToCamelCase(json: Json): Json =
    KeyConverter.transformKeys(json, KeyConverter.sc2cc).run

  def snakeCaseToCamelCase(jsonString: String): Either[ParsingFailure, String] = {
    for {
      json <- parser.parse(jsonString)
      camelCasedJson = KeyConverter.snakeCaseToCamelCase(json)
    } yield {
      noSpaces.print(camelCasedJson)
    }
  }

  def snakeCaseToCamelCaseJson(jsonString: String): Either[ParsingFailure, Json] = {
    for {
      json <- parser.parse(jsonString)
      camelCasedJson = KeyConverter.snakeCaseToCamelCase(json)
    } yield camelCasedJson
  }

}