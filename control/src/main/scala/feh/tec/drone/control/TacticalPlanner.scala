package feh.tec.drone.control

import akka.actor.Actor
import feh.tec.drone.control.TacticalPlanner.{WaypointReached, WaypointsControl, SetWaypoints}
import feh.tec.matlab.{DroneSimulation, MatlabSimClient}
import feh.tec.drone.control.util.Math.PowWrapper
import feh.tec.drone.control.Coordinate.CoordinateOps
import feh.tec.drone.control.matlab.DynControl
import akka.util.Timeout
import feh.tec.drone.control.Config.SimConfig
import feh.tec.drone.control.DroneApiCommands.MoveFlag
import scala.concurrent.Future
import feh.tec.drone.control.DataForwarder.Forward
import scala.reflect.runtime.universe._

object TacticalPlanner{

  trait WaypointsControl
  case class SetWaypoints(points: Environment#Coordinate*) extends WaypointsControl

  trait WaypointsNotification
  case class WaypointReached(point: Environment#Coordinate, time: Long) extends WaypointsNotification
  case class WaypointCannotBeReached(point: Environment#Coordinate, reason: String) extends WaypointsNotification
}

/** Tactical planner calculates and executes trajectory from points, given to it by Strategic Planner
 */
trait TacticalPlanner extends Actor{
  var waypoints: Seq[Environment#Coordinate] = Nil

  def nextWaypoint() = waypoints = if(waypoints.nonEmpty) waypoints.tail else Nil
  def destination = waypoints.headOption

  def msgWaypoints(msg: WaypointsControl) = msg match {
    case SetWaypoints(wp@_*) => waypoints = wp
  }

  val poseEstimationFeedTag: TypeTag[_ <: AbstractPoseEstimationFeed]
  lazy val PoseEstimationFeed = BuildForwardMatcher(poseEstimationFeedTag)

  def poseEstimation: Pose
  
  type Input
  /** what's next to do
   */
  def command: Input => Future[ControlCommand]
  
  def msgPoseEstimated(pose: Pose)

  def receive = {
    case wp: WaypointsControl => msgWaypoints(wp)
    case PoseEstimationFeed(pose) => msgPoseEstimated(pose)
  }
}

trait TacticalPlannerHelper {
  self: TacticalPlanner =>

  private def pos = poseEstimation.position

  def distanceTo(point: Environment#Coordinate) = math.sqrt((point - pos).map(_ ^ 2).seq.sum)
  def closeToPoint_?(distance: Double)(point: Environment#Coordinate) = distanceTo(point) <= distance

}


trait MatlabDynControlTacticalPlanner extends TacticalPlanner{
  def matlab: MatlabSimClient
  def simConf: SimConfig
  implicit def execContext = simConf.execContext

  lazy val controlSim = new DroneSimulation[DynControl](new DynControl, matlab, simConf.defaultTimeout)

  def startControl(){ controlSim.start(simConf.simStartTimeout) }

  import controlSim._

  def setControlDestination(position: Environment#Coordinate, yaw: Option[Double] = None) = {
    setParam(_.x, position.x)
    setParam(_.y, position.y)
    setParam(_.z, position.z)
    yaw.foreach(setParam(_.yaw, _))
  }

  def getControl(pose: Pose, nav: NavdataDemo) = {
    val c = execMethod(_.readControl)
    execMethod(_.writeNavdata,
      pose.position.x, pose.position.y, pose.position.z,
      nav.pitch, nav.roll, nav.yaw,
      nav.vx, nav.vy, nav.vz,
      nav.dpitch, nav.droll, nav.dyaw
    )
    c
  }
}

class StraightLineTacticalPlanner(val env: Environment,
//                                  val strategicPlanner: StrategicPlanner,
                                  val matlab: MatlabSimClient,
                                  val simConf: SimConfig,
                                  val poseEstimationFeedTag: TypeTag[_ <: AbstractPoseEstimationFeed],
                                  val pointDistance: Double)
  extends MatlabDynControlTacticalPlanner with TacticalPlannerHelper
{
  type Input = NavdataDemo
  var poseEstimation = Pose(env.zero, Orientation(0, 0, 0))


  def msgPoseEstimated(pose: Pose) = poseEstimation = pose

  private def isCloseToPoint = closeToPoint_?(pointDistance) _

  /** what's next to do
    */
  def command = nav => {
    if(destination.nonEmpty && isCloseToPoint(poseEstimation.position)){
//      strategicPlanner ! WaypointReached(destination.get, System.currentTimeMillis())
      nextWaypoint()
      destination foreach (setControlDestination(_))
    }

    getControl(poseEstimation, nav).map (c =>
      DroneApiCommands.Move(MoveFlag.default, c.roll, c.pitch, c.yaw, c.gaz)
    )
  }
}