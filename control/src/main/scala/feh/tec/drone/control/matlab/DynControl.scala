package feh.tec.drone.control.matlab

import feh.tec.matlab._
import feh.tec.drone.control.matlab.DynControl.Control
import scala.reflect._
import feh.util.FileUtils._
import feh.tec.matlab.GenericMethod
import feh.tec.matlab.GetWorkspaceVarStructure

object DynControl{
  case class Control(pitch: Double, roll: Double, yaw: Double, gaz: Double)
  
  object Control{
    def zero = Control(0,0,0,0)
  }
}

class DynControl extends Model("quadrotor_control", "realsys", dir = /){
  val x = Param.double("x")
  val y = Param.double("y")
  val z = Param.double("z")
  val yaw = Param.double("yaw")

  val readControl: GetWorkspaceVarStructure[DynControl.Control] =
    GetWorkspaceVarStructure("readControl", fields =>
      fields.get[String]("error")
        .map{ err => throw ModelMethodException(this, readControl, err) }
        .getOrElse(
          Control(
            fields[Double]("pitch"),
            fields[Double]("roll"),
            fields[Double]("yaw"),
            fields[Double]("gaz")
          )
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
