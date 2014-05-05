package feh.tec.drone.control

import feh.tec.drone.control.util.UnsignedInt
import feh.util.InUnitInterval

sealed trait ControlCommand

package object DroneApiCommands{

  case object Takeoff extends ControlCommand
  case object Land extends ControlCommand
  case object EmergencyStop extends ControlCommand
  case object CancelEmergencyStop extends ControlCommand

  /** Move the drone (AT*PCMD)
   *
   * @param ﬂag ﬂag enabling the use of progressive commands and/or the Combined Yaw mode
   * @param roll drone left-right tilt
   * @param pitch drone front-back tilt
   * @param gaz drone vertical speed
   * @param yaw drone angular speed
   */
  case class Move(ﬂag: MoveFlag,
                  roll: InUnitInterval,
                  pitch: InUnitInterval,
                  yaw: InUnitInterval,
                  gaz: InUnitInterval) extends ControlCommand

  case class MoveFlag(absoluteControl: Boolean,
                      combinedYaw: Boolean,
                      progressiveCommands: Boolean)
  object MoveFlag{
    def default = MoveFlag(false, false, false)
  }

  case object ResetWatchdog extends ControlCommand
}


//trait AT_REF extends ControlCommandInterpreter[]