package feh.tec.drone.control

import akka.actor.{Actor, ActorSystem, Props, ActorRef}
import feh.tec.matlab.{DroneSimulation, Model, MatlabSimClient}
import akka.pattern.ask
import scala.util.{Failure, Success}
import scala.concurrent.Future
import feh.tec.matlab.server.MatlabAsyncServer
import feh.util._
import akka.event.Logging
import feh.tec.drone.control.LifetimeController.Stage

trait Core {
  def env: Environment

  def controller: ActorRef
  def forwarder: ActorRef

  def feedReaders: Map[DataFeed, FeedReaderProps]
  def feedNotifiers: Map[DataFeed, FeedNotifierProps]

  def control: Set[ActorRef]

  def feeds: Set[DataFeed] = feedReaders.keySet ++ feedNotifiers.keySet

  def start() = {
    controller ! Control.Start
    forwarder ! Control.Start
    control foreach(_ ! Control.Start)
  }

  def stop() = {
    controller ! Control.Stop
    forwarder ! Control.Stop
    control foreach(_ ! Control.Stop)
  }
}

trait NavigationCore extends Core{

  def navigationFeed: DataFeed
  def navigationFeedReader: FeedReaderProps

  def poseEstimationFeed: DataFeed
  def poseEstimator: FeedNotifierProps

  abstract override def feedReaders = super.feedReaders + (navigationFeed -> navigationFeedReader)
  abstract override def feedNotifiers = super.feedNotifiers + (poseEstimationFeed -> poseEstimator)
}

trait ControlCore extends Core{
  def tacticalPlanner: ActorRef
//  def strategicPlanner
//  def emergencyControl
  abstract override def control = super.control ++ Set(tacticalPlanner)
}

trait MatlabControlCore extends ControlCore{
  val controlMatlab: MatlabSimClient
  val controlConfig: Config.SimConfig
}

trait MatlabEmulationCore extends Core{
  val emulationMatlab: MatlabSimClient
  val emulationModel: Model
  val emulationSim: DroneSimulation[_]
  val emulationConfig: Config.SimConfig

}

trait LifetimeController extends Actor{
  def state: LifetimeController.State.Value
  def state_=(v: LifetimeController.State.Value)
}

object LifetimeController{

  object State extends Enumeration{
    type State = Value
    val Uninitialized, StartingUp, Running, Terminating, Terminated, Exceptional = Value
  }

  case class Stage(name: String, exec: Lifted[Future[Any]]){
    override def toString = s"Stage($name)"
  }

  class LifetimeException(val state: State.Value, msg: String) extends Exception(s"[$state] $msg")

  case class StartupException(errors: Seq[Throwable], stage: Stage)
    extends LifetimeException(State.StartingUp,
      s"${errors.length} errors on startup during $stage:\n${errors mkString "\n"}")

  trait SimulationErrorListener extends LifetimeController{
    def simulations: Seq[MatlabSimClient]

    protected lazy val simByServer = simulations.zipMap(_.server.anchorPath).map(_.swap).toMap

    def receive = {
      case MatlabAsyncServer.Error(err) => simulationError(simByServer(sender.path), err)
    }

    def simulationError(sim: MatlabSimClient, err: Throwable)
  }

  trait StartupController extends LifetimeController{
    def startupStages: Seq[Stage]
    def currentStage: Option[Stage]
    def startupTerminated()
  }
  
  trait LifetimeControllerBase extends LifetimeController{
    private var _state: State.Value = State.Uninitialized

    def state: State.Value = _state
    def state_=(v: State.Value) = {
      val old = state
      _state = v
      stateChanged(old, v)
    }

    protected def stateChanged(old: State.Value, n: State.Value) {}

//    def receive: Actor.Receive = Map()
  }
}

object CoreBase{
  case class ForwarderParams(controller: ActorRef,
                             feedReaders: Map[DataFeed, FeedReaderProps],
                             feedNotifiers: Map[DataFeed, FeedNotifierProps])
}

abstract class CoreBase(val env: Environment,
                        controllerProps: Props,
                        forwarderProps: CoreBase.ForwarderParams => Props
                        /*controlProps: Set[Props],
                        val feedReaders: Map[DataFeed, FeedReaderProps] = Map(),
                        val feedNotifiers: Map[DataFeed, FeedNotifierProps] = Map()*/)
                       (implicit val asys: ActorSystem) extends Core{
  lazy val controller = asys.actorOf(controllerProps, "core-controller")
  lazy val forwarder = asys.actorOf(
    forwarderProps(CoreBase.ForwarderParams(controller, feedReaders, feedNotifiers)),
    "core-forwarder"
  )

  def feedReaders: Map[DataFeed, FeedReaderProps] = Map()
  def feedNotifiers: Map[DataFeed, FeedNotifierProps] = Map()
  def control: Set[ActorRef] = Set()

  controller;forwarder // init lazy val, it's done to override controller without overridden object creation (is/was scala ?bug?)
}