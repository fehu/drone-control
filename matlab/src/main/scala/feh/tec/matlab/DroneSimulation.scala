package feh.tec.matlab

import feh.util.FileUtils._
import scala.reflect.ClassTag
import scala.concurrent.{ExecutionContext, Future}
import akka.util.Timeout
import scala.concurrent.duration._

case class Param[T : ClassTag](name: String, asString: T => String, parseString: String => T){
  def clazz = scala.reflect.classTag[T].runtimeClass
}

object Param{
  def double(name: String) = Param[Double](name, _.toString, _.toDouble)
}

case class Model(name: String, path: Path, params: List[Param[_]])

class DroneSimulation(val model: Model, matlab: MatlabSimClient, implicit val responseTimeout: Timeout)
                     (implicit execContext: ExecutionContext)
{
  def init(): Future[Unit] = matlab
    .feval("run", model.path.file.getAbsolutePath :: Nil, 0)
    .map(_ => {})

  def start(): Future[Unit] = matlab.startSim(model.name)(30 seconds).map(_ => {})

  def stop(): Future[Unit] = matlab
    .feval("set_param", model.name :: "SimulationCommand" :: "stop" :: Nil, 0)
    .map{_ => Unit}

  def setParam[T](param: Param[T], v: T): Future[Unit] = matlab
    .feval("set_param", fullname(param) :: "Value" :: param.asString(v) :: Nil, 0)
    .map(_ => {})

  def getParam[T](param: Param[T]): Future[T] = matlab
    .feval("get_param", fullname(param) :: "Value" :: Nil, 1)
    .map{
      case Array(str: String) => param.parseString(str)
    }
  
  private def fullname(param: Param[_]) = model.name + "/" + param.name
}

object PCorke{
  lazy val x = Param.double("x")
  lazy val y = Param.double("y")
  lazy val z = Param.double("z")
  lazy val yaw = Param.double("yaw")

  def params = x :: y :: z :: yaw :: Nil
  
  object Model extends Model("sl_quadrotor", "matlab" / "sl_quadrotor.mdl", params)
}
