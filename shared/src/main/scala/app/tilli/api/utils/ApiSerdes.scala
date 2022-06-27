package app.tilli.api.utils

object ApiSerdes {

  object Serializer extends sttp.tapir.json.circe.TapirJsonCirce

}
