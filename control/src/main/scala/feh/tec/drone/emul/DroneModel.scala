package feh.tec.drone.emul

import feh.tec.matlab.{GetWorkspaceVarStructure, GetWorkspaceVar, Method, QuadModel}
import feh.tec.drone.control.{ControlState, NavdataDemo}
import feh.tec.drone.control.util.UnsignedInt

class DroneModel extends QuadModel.Drone {
  lazy val navdataDemo = GetWorkspaceVarStructure[NavdataDemo]("get_navdata_demo", params =>
      NavdataDemo(
        ctrl_state = ControlState.Default, // todo
        batteryVoltage = 0, //todo
        pitch = params[Double]("pitch").toFloat,
        roll = params[Double]("roll").toFloat,
        yaw = params[Double]("yaw").toFloat,
        altitude = params[Double]("altitude").toInt,
        vx = params[Double]("dx").toFloat,
        vy = params[Double]("dy").toFloat,
        vz = params[Double]("dz").toFloat,
        dpitch = params[Double]("dpitch").toFloat,
        droll = params[Double]("droll").toFloat,
        dyaw = params[Double]("dyaw").toFloat
      )
  )

  override def methods = navdataDemo :: Nil
}
