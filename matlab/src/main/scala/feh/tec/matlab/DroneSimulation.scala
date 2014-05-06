package feh.tec.matlab

import feh.util.FileUtils._
import scala.reflect.ClassTag
import scala.concurrent.{ExecutionContext, Future}
import akka.util.Timeout
import scala.concurrent.duration._
import akka.actor.{ActorIdentity, Identify}
import akka.pattern.ask
import feh.util.InUnitInterval
import feh.util._

object Structure{
  implicit def matlabStructureWrapper(ms: Structure) = ms.fields

  def fromMatlab(name: String): Array[Any] => Structure = {
    case Array(Array(names @ _*), Array(Array(vals @ _*))) =>
      Structure(name,
        names.zip(vals).map{ case (k: String, v) => k -> v.asInstanceOf[Array[Double]].head}.toMap[String, Any]
      )
  }
}
case class Structure(name: String, fields: Map[String, Any]){
  def apply[T](field: String): T = fields(field).asInstanceOf[T]
  def get[T](field: String): Option[T] = fields.get(field).withFilter(_.isInstanceOf[T]).map(_.asInstanceOf[T])
}

case class Param[T : ClassTag](name: String, asString: T => String, parseString: String => T){
  def clazz = scala.reflect.classTag[T].runtimeClass
}

object Param{
  def double(name: String) = Param[Double](name, _.toString, _.toDouble)
  def unit(name: String) = Param[InUnitInterval](name, _.toString, _.toDouble)
  def bool(name: String) = Param[Boolean](name, x => if(x) "1" else "0", _.toBoolean)
}

trait Method[R]{
  def params: List[Any]
  def result: Array[Any] => R
}

case class GetWorkspaceVar[R : ClassTag](name: String, result: Array[Any] => R) extends Method[R]{
  def params = Nil
}

case class GetWorkspaceVarStructure[R : ClassTag](name: String, sresult: Structure => R) extends Method[R]{
  def result = sresult compose Structure.fromMatlab(name)
  def params = Nil
}

case class GenericMethod[R](name: String, params: List[ClassTag[_]], nReturn: Int, result: Array[Any] => R) extends Method[R]

abstract class Model(val name: String, val execLoopBlock: String, dir: Path){
  def params: List[Param[_]]
  def methods: List[Method[_]]

  val path: Path = dir / (name + ".mdl")
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
      matlab.eval(name, 1).map(func.result)
    case GetWorkspaceVarStructure(name, parse) =>
      assert(params.isEmpty)
      matlab.eval(name, 1).map(parse compose Structure.fromMatlab(name))
    case GenericMethod(name, pTags, nReturn, result) =>
      assert(pTags.length == params.length, "wrong params number")
      for((p, tag) <- params zip pTags)
        assert(p.getClass == tag.runtimeClass, s"$tag is required, ${p.getClass} found")
      matlab.feval(name, params.toList, nReturn) map result
  }

  def testConnection(implicit timeout: Timeout = defaultTimeout): Future[Unit] =
    (matlab.server ? Identify).mapTo[ActorIdentity].map(_ => {})
  
  private def fullname(param: Param[_]) = model.name + "/" + param.name
}

object QuadModel{
  type PCorke = PCorke.type
  object PCorke extends Model("sl_quadrotor", "Quadrotor plot/Plotter", "matlab"){
    lazy val x = Param.double("x")
    lazy val y = Param.double("y")
    lazy val z = Param.double("z")
    lazy val yaw = Param.double("yaw")

    def params = x :: y :: z :: yaw :: Nil
    def methods = Nil
  }

  class Drone extends Model("quadrotor_emul", "Quadrotor plot/Plotter", dir = /){
    lazy val roll = Param.unit("roll") // why unit ??
    lazy val pitch = Param.unit("pitch")
    lazy val yaw = Param.double("yaw")

    lazy val gaz = Param.double("gaz")

    def params = roll :: pitch :: gaz :: yaw :: Nil
    def methods: List[Method[_]] = Nil
  }
  object Drone extends Drone
}

