package feh.tec.drone.control

import akka.actor.{ActorSystem, Props, ActorRef}
import feh.tec.matlab.{DroneSimulation, Model, MatlabSimClient}
import akka.pattern.ask
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext

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

/**
 * loses generality on control, for usage with matlab simulation and control
 */
trait CoreSequentialStart extends Core{
  self: MatlabEmulationCore with MatlabControlCore =>

  def startExecContext: ExecutionContext
  
  override def start(){
    implicit def context = startExecContext
    controller.ask(Control.Start)(emulationConfig.simStartTimeout) flatMap  { _ =>
      forwarder ! Control.Start
      (tacticalPlanner ? Control.Start)(controlConfig.simStartTimeout)
    } onComplete {
      case Success(_) => // continue with execution
      case Failure(err) => throw err
    }
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