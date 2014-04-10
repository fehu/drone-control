package feh.tec.matlab

import feh.util.FileUtils._
import scala.reflect.ClassTag
import scala.concurrent.{ExecutionContext, Future}
import akka.util.Timeout
import scala.concurrent.duration._
import akka.actor.{ActorIdentity, Identify}
import akka.pattern.ask
import feh.util.InUnitInterval

case class Param[T : ClassTag](name: String, asString: T => String, parseString: String => T){
  def clazz = scala.reflect.classTag[T].runtimeClass
}

object Param{
  def double(name: String) = Param[Double](name, _.toString, _.toDouble)
  def unit(name: String) = Param[InUnitInterval](name, _.toString, _.toDouble)
  def bool(name: String) = Param[Boolean](name, x => if(x) "1" else "0", _.toBoolean)
}

trait Method[R]{
  def params: List[ClassTag[_]]
  def parse: Array[Any] => R
}

case class GetWorkspaceVar[R : ClassTag](name: String, parse: Array[Any] => R) extends Method[R]{
  def params = Nil
}

abstract class Model(val name: String, val execLoopBlock: String, val path: Path){
  def params: List[Param[_]]
  def methods: List[Method[_]]
}

class DroneSimulation[M <: Model](val model: M, matlab: MatlabSimClient, val defaultTimeout: Timeout)
                                 (implicit execContext: ExecutionContext)
{
  def init(timeout: Timeout): Future[Unit] = matlab
    .feval("run", model.path.file.getAbsolutePath :: Nil, 0)(timeout)
    .map(_ => {})

  def start(timeout: Timeout): Future[Unit] = matlab.startSim(model.name, model.execLoopBlock)(timeout).map(_ => {})

  def stop(implicit timeout: Timeout = defaultTimeout): Future[Unit] = matlab
    .feval("set_param", model.name :: "SimulationCommand" :: "stop" :: Nil, 0)
    .map{_ => Unit}

  def setParam[T](paramSel: M => Param[T], v: T)(implicit timeout: Timeout = defaultTimeout): Future[Unit] =
    setParam(paramSel(model), v)
  def setParam[T](param: Param[T], v: T)(implicit timeout: Timeout = defaultTimeout): Future[Unit] = matlab
    .feval("set_param", fullname(param) :: "Value" :: param.asString(v) :: Nil, 0)
    .map(_ => {})

  def getParam[T](paramSel: M => Param[T])(implicit timeout: Timeout = defaultTimeout): Future[T] =
    getParam(paramSel(model))
  def getParam[T](param: Param[T])(implicit timeout: Timeout = defaultTimeout): Future[T] = matlab
    .feval("get_param", fullname(param) :: "Value" :: Nil, 1)
    .map{
      case Array(str: String) => param.parseString(str)
    }

  def execMethod[R](funcSel: M => Method[R], params: Any*)(implicit timeout: Timeout = defaultTimeout): Future[R] =
    execMethod(funcSel(model), params: _*)
  def execMethod[R](func: Method[R], params: Any*)(implicit timeout: Timeout = defaultTimeout): Future[R] = func match{
    case GetWorkspaceVar(name, _) =>
      assert(params.isEmpty)
      matlab.eval(name, 1).map(func.parse)
  }

  def testConnection(implicit timeout: Timeout = defaultTimeout): Future[Unit] =
    (matlab.server ? Identify).mapTo[ActorIdentity].map(_ => {})
  
  private def fullname(param: Param[_]) = model.name + "/" + param.name
}

object QuadModel{
  type PCorke = PCorke.type
  object PCorke extends Model("sl_quadrotor", "Quadrotor plot/Plotter", "matlab" / "sl_quadrotor.mdl"){
    lazy val x = Param.double("x")
    lazy val y = Param.double("y")
    lazy val z = Param.double("z")
    lazy val yaw = Param.double("yaw")

    def params = x :: y :: z :: yaw :: Nil
    def methods = Nil
  }

  class Drone extends Model("quadrotor_emul", "Quadrotor plot/Plotter", "matlab" / "quadrotor_emul.mdl"){
    lazy val roll = Param.unit("roll")
    lazy val pitch = Param.unit("pitch")
    lazy val yaw = Param.double("yaw")

    lazy val gaz = Param.double("gaz")

    lazy val autoZ = Param.bool("autoZ")
    lazy val z = Param.double("z")

    def params = roll :: pitch :: gaz :: yaw :: autoZ :: z :: Nil
    def methods: List[Method[_]] = Nil
  }
  object Drone extends Drone
}

