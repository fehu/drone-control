package feh.tec.drone.control.matlab

import feh.tec.matlab.{GenericMethod, GetWorkspaceVarStructure, Param, Model}
import feh.tec.drone.control.matlab.DynControl.Control
import scala.reflect._
import feh.util.FileUtils._

object DynControl{
  case class Control(pitch: Double, roll: Double, yaw: Double, gaz: Double)
}

class DynControl extends Model("quadrotor_control", "realsys", dir = /){
  val x = Param.double("x")
  val y = Param.double("y")
  val z = Param.double("z")
  val yaw = Param.double("yaw")

  val readControl = GetWorkspaceVarStructure("readControl",
    fields =>
      Control(
        fields[Double]("pitch"),
        fields[Double]("roll"),
        fields[Double]("yaw"),
        fields[Double]("gaz")
      )
  )

  val writeNavdata = GenericMethod[Unit]("writeNavdata",
    classTag[java.lang.Double] :: // x
      classTag[java.lang.Double] :: // y
      classTag[java.lang.Double] :: // z
      classTag[java.lang.Float] :: // pitch
      classTag[java.lang.Float] :: // roll
      classTag[java.lang.Float] :: // yaw
      classTag[java.lang.Float] :: // dx
      classTag[java.lang.Float] :: // dy
      classTag[java.lang.Float] :: // dz
      classTag[java.lang.Float] :: // dpitch
      classTag[java.lang.Float] :: // droll
      classTag[java.lang.Float] :: Nil,// dyaw
    nReturn = 0,
    result = _ => {}
  )

  def params = x :: y :: z :: yaw :: Nil
  def methods = ???
}
