package feh.tec.drone.control

import feh.tec.drone.control.util.UnsignedInt

trait NavigationData

case class NavdataDemo(ctrl_state: ControlState,
                       batteryVoltage: UnsignedInt, // battery voltage filtered (mV)
                       pitch: Float,                // UAV's pitch in milli-degrees
                       roll: Float,                 // UAV's roll in milli-degrees
                       yaw: Float,                  // UAV's yaw in milli-degrees
                       altitude: Int,               // UAV's altitude in centimeters
                       vx: Float,                   // UAV's estimated linear velocity
                       vy: Float,                   // UAV's estimated linear velocity
                       vz: Float                    // UAV's estimated linear velocity
//                       detection_camera_type: Null  // ?????
                        ) extends NavigationData

trait ControlState

/** Control states from control_states.h
 */
object ControlState{
  case object Default extends ControlState
  case object Init extends ControlState
  case object Landed extends ControlState
  case object Flying extends ControlState
  case object Hovering extends ControlState
  case object Test extends ControlState
  case object TransTakeoff extends ControlState
  case object TransGotoFix extends ControlState
  case object TransLanding extends ControlState
  case object TransLooping extends ControlState

  /*
  #ifdef CTRL_STATES_STRING
  static ctrl_string_t ctrl_states[] = {
  #else
  typedef enum {
  #endif
    CVARZ( CTRL_DEFAULT ),
    CVAR( CTRL_INIT ),
    CVAR( CTRL_LANDED ),
    CVAR( CTRL_FLYING ),
    CVAR( CTRL_HOVERING ),
    CVAR( CTRL_TEST ),
    CVAR( CTRL_TRANS_TAKEOFF ),
    CVAR( CTRL_TRANS_GOTOFIX ),
    CVAR( CTRL_TRANS_LANDING ),
    CVAR( CTRL_TRANS_LOOPING ),
    //CVAR( CTRL_TRANS_NO_VISION ),
  #ifndef CTRL_STATES_STRING
    CTRL_NUM_STATES
  } CTRL_STATES;
  #else
  };
  #endif
 */
}

trait NavdataDemoFeed extends DataFeed{
  type Data = NavdataDemo
}

