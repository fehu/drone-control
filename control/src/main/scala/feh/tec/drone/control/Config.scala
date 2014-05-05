package feh.tec.drone.control

import akka.util.Timeout

object Config {
  case class SimTimeouts(default: Timeout, simStart: Timeout)
}
