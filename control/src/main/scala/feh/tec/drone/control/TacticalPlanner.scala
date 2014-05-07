package feh.tec.drone.control

import akka.actor.{ActorRef, Props, Actor}
import feh.tec.drone.control.TacticalPlanner.{WaypointsControl, SetWaypoints}
import feh.tec.matlab.{ModelMethodException, DroneSimulation, MatlabSimClient}
import feh.tec.drone.control.util.Math.PowWrapper
import feh.tec.drone.control.Coordinate.CoordinateOps
import feh.tec.drone.control.matlab.DynControl
import feh.tec.drone.control.Config.SimConfig
import feh.tec.drone.control.DroneApiCommands.MoveFlag
import scala.concurrent.{Await, Future}
import scala.reflect.runtime.universe._
import feh.tec.drone.control.Control.Message
import akka.event.Logging
import feh.tec.drone.control.DataForwarder.{Unsubscribe, Subscribe}
import feh.tec.drone.emul.Emulator.NavdataDemoFeed
import feh.tec.drone.control.LifetimeController.RunException
import feh.util._

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
    case SetWaypoints(wp@_*) =>
      waypoints = wp
      sendCommand()
  }


  def poseEstimation: Pose
  
  type Input
  /** what's next to do
   */
  def command: Input => Future[ControlCommand]

  def sendCommand()

  def msgPoseEstimated(pose: Pose)

  def msgControl(msg: Control.Message)

  var navData: NavdataDemo = NavdataDemo.zero

  def navdataFeed: NavdataDemoFeed
  def poseEstimationFeed: AbstractPoseEstimationFeed

  protected val NavdataMatch = new BuildFeedMatcher(navdataFeed)
  protected val PoseEstimationMatch = new BuildFeedMatcher(poseEstimationFeed)

  def receive = {
    case NavdataMatch(data) => navData = data
    case control: Control.Message => msgControl(control)
    case wp: WaypointsControl => msgWaypoints(wp)
    case PoseEstimationMatch(pose) => msgPoseEstimated(pose)
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
  def forwarder: ActorRef
  
  def simConf: SimConfig
  implicit def execContext = simConf.execContext

  lazy val controlSim = new DroneSimulation[DynControl](new DynControl, matlab, simConf.defaultTimeout)

  protected val log = Logging(context.system, this)

  def start() =
    controlSim.start(simConf.simStartTimeout) map { _ =>
      forwarder ! Subscribe(navdataFeed)
      forwarder ! Subscribe(poseEstimationFeed)
      log.info("Tactical planner started")
    }

  def stop(){
    controlSim.stop.map(_ => log.info("control simulation stopped")) onComplete (sender() !)
    forwarder ! Unsubscribe(navdataFeed)
    forwarder ! Unsubscribe(poseEstimationFeed)
    log.info("Tactical planner stopped")
  }

  def msgControl(msg: Message) = msg match{
    case Control.Start => start() onComplete (sender !)
    case Control.Stop => stop()
  }

  import controlSim._

  def setControlDestination(position: Environment#Coordinate, yaw: Option[Double] = None) = {
    setParam(_.x, position.x)
    setParam(_.y, position.y)
    setParam(_.z, position.z)
    yaw.foreach(setParam(_.yaw, _))
  }

  def getControl(pose: Pose, nav: NavdataDemo) = {
    val c =
      execMethod(_.readControl) recoverWith{
        case ModelMethodException(`model`, _, "no new control data available") => Future { noControlDataAvailable }
      }
    execMethod(_.writeNavdata,
      pose.position.x, pose.position.y, pose.position.z,
      nav.pitch, nav.roll, nav.yaw,
      nav.vx, nav.vy, nav.vz,
      nav.dpitch, nav.droll, nav.dyaw
    )
    c
  }

  private var isFirst = true
  def noControlDataAvailable =
    if(isFirst) {
      isFirst = false
      DynControl.Control.zero
    }
    else sys.error("noControlDataAvailable")
}

class StraightLineTacticalPlanner(val env: Environment,
                                  val controller: ActorRef,
                                  val forwarder: ActorRef,
//                                  val strategicPlanner: StrategicPlanner,
                                  val matlab: MatlabSimClient,
                                  val simConf: SimConfig,
                                  val navdataFeed: NavdataDemoFeed,
                                  val poseEstimationFeed: AbstractPoseEstimationFeed,
                                  val pointDistance: Double)
  extends MatlabDynControlTacticalPlanner with TacticalPlannerHelper
{
  type Input = NavdataDemo
  var poseEstimation = Pose(env.zero, Orientation(0, 0, 0))

  def msgPoseEstimated(pose: Pose) = {
    poseEstimation = pose

    sendCommand()
  }

  def sendCommand() = {
    log.info("called sendCommand")
    command(navData) map {
      c =>
        log.info("Sending command to drone controller: " + c)
        controller ! c
    } onFailure{
      case err: Throwable =>
        log.debug("failed to generate/send command " + err) // todo remove
        forwarder ! RunException(self, "failed to generate/send command", err)
    }
  }


//  override def start(): Unit = {
//    super.start()
//  }

  private def isCloseToPoint = closeToPoint_?(pointDistance) _

  /** what's next to do
    */
  def command = nav => {
    log.info("next command requested from tactical planner")
    if(destination.nonEmpty && isCloseToPoint(poseEstimation.position)){
//      strategicPlanner ! WaypointReached(destination.get, System.currentTimeMillis())
      nextWaypoint()
      destination foreach (setControlDestination(_))
    }

    getControl(poseEstimation, nav).map (c =>
      DroneApiCommands.Move(MoveFlag.default, c.roll, c.pitch, c.yaw, c.gaz)
    ) map (_ $${
      s => log.info("got control data from matlab: " + s)
    })
  }

  override def receive = super.receive orElse{
    case StraightLineTacticalPlanner.SendCommand => sender ! sendCommand()
  }
}

object StraightLineTacticalPlanner{
  def props(env: Environment,
            controller: ActorRef,
            forwarder: ActorRef,
            matlab: MatlabSimClient,
            simConf: SimConfig,
            navdataFeed: NavdataDemoFeed,
            poseEstimationFeed: AbstractPoseEstimationFeed,
            pointDistance: Double) =
    Props(classOf[StraightLineTacticalPlanner], env, controller, forwarder, matlab, simConf,
      navdataFeed, poseEstimationFeed, pointDistance)

  case object SendCommand
}