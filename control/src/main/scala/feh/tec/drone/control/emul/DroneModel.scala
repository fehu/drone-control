package feh.tec.drone.control.emul

import feh.tec.matlab.{GetWorkspaceVar, Method, QuadModel}
import feh.tec.drone.control.{ControlState, NavdataDemo}
import feh.tec.drone.control.util.UnsignedInt

class DroneModel extends QuadModel.Drone {
  lazy val navdataDemo = GetWorkspaceVar[NavdataDemo]("get_navdata_demo", {
    case Array(Array(names @ _*), Array(Array(vals @ _*))) =>
      val params = names.zip(vals).map{ case (k: String, v) => k -> v.asInstanceOf[Array[Double]].head}.toMap[String, Double]
      NavdataDemo(
        ctrl_state = ControlState.Default, // todo
        batteryVoltage = UnsignedInt(0), //todo
        pitch = params("pitch").toFloat,
        roll = params("roll").toFloat,
        yaw = params("yaw").toFloat,
        altitude = params("altitude").toInt,
        vx = params("dx").toFloat,
        vy = params("dy").toFloat,
        vz = params("dz").toFloat
      )
  }
  )

  override def methods = navdataDemo :: Nil
}
