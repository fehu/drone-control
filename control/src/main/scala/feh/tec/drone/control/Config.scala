package feh.tec.drone.control

import akka.util.Timeout
import scala.concurrent.ExecutionContext

object Config {
  case class SimConfig(defaultTimeout: Timeout, simStartTimeout: Timeout, implicit val execContext: ExecutionContext)
}
