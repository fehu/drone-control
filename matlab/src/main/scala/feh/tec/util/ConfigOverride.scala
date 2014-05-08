package feh.tec.util

import com.typesafe.config.Config

/** Global akka Config override
 * some classes use this config instead of the one, provided by factory
 */
object ConfigOverride {
  var config: Option[Config] = None

  def isDefined = config.isDefined
}
