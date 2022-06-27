package app.tilli.utils

import cats.effect.{Resource, Sync}
import com.typesafe.config.ConfigFactory
import pureconfig.module.catseffect.syntax.CatsEffectConfigSource
import pureconfig.{ConfigReader, ConfigSource}

import scala.reflect.ClassTag

trait ApplicationConfig {

  def loadFile[F[_] : Sync, A: ClassTag](
    environment: String,
    configFile: String
  )(implicit reader: ConfigReader[A]): Resource[F, A] = {
    val relativePath = s"configuration/$environment/$configFile"
    Resource.eval {
      ConfigSource.fromConfig(ConfigFactory.load(relativePath)).loadF[F, A]
    }
  }

  def getEnvironment[F[_] : Sync]: Resource[F, String] = Resource.eval(Sync[F].delay(sys.env("ENVIRONMENT")))

  def apply[F[_] : Sync, A: ClassTag](
    file: String = "application.conf",
  )(implicit reader: ConfigReader[A]): Resource[F, A] =
    getEnvironment.flatMap(env => loadFile(env, file))

}

object ApplicationConfig extends ApplicationConfig