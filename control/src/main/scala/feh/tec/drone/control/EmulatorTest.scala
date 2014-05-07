package feh.tec.drone.control

import feh.tec.drone.emul.{DroneModel, EmulatorFeedChannelStub, Emulator}
import scala.concurrent.duration._
import feh.tec.matlab.{DroneSimulation, MatlabSimClient}
import feh.tec.drone.control.Config.SimConfig
import akka.actor.{Props, ActorRef, ActorSystem}
import feh.tec.matlab.server
import scala.concurrent.ExecutionContext


object EmulatorTest {
  val readFreq: FiniteDuration = 10 millis span

  class Core(implicit actorSys: ActorSystem) extends CoreBase
    with NavigationCore with MatlabControlCore with MatlabEmulationCore with CoreSequentialStartImpl
  {

    def env = new SimpleEnvironment()

    def controllerProps = Emulator.controllerProps(emulationSim, emulationConfig.simStartTimeout)

    def forwarderProps = DataForwarder.props(new EmulatorFeedChannelStub, lifetimeController, _ => None,
      aref => feedReaders, feedNotifiers, readFreq)(actorSys)

    def serviceExecContext = asys.dispatcher
    import asys.dispatcher

    def simulations = controlMatlab :: emulationMatlab :: Nil

    protected lazy val lifetimeController: ActorRef =
      asys.actorOf(Props(classOf[CoreSequentialStartImpl.LifeController], stages, simulations), "startup-controller")

    lazy val controlMatlab = new MatlabSimClient(asys.actorSelection(server.DynControl.path))
    lazy val controlConfig = SimConfig(
      defaultTimeout = 1 second,
      simStartTimeout = 30 seconds,
      simStopTimeout =  100 millis,
      execContext = asys.dispatcher)

    lazy val emulationMatlab = new MatlabSimClient(asys.actorSelection(server.DroneEmul.path))
    lazy val emulationModel = new DroneModel
    lazy val emulationConfig = SimConfig(
      defaultTimeout = 20 millis,
      simStartTimeout = 30 seconds,
      simStopTimeout =  100 millis,
      execContext = asys.dispatcher
    )
    lazy val emulationSim =
      new DroneSimulation[Emulator.Model](emulationModel, emulationMatlab, emulationConfig.defaultTimeout)

    lazy val tacticalPlanner = asys.actorOf(StraightLineTacticalPlanner
      .props(env, controller, forwarder, controlMatlab, controlConfig, navigationFeed, poseEstimationFeed,
        pointDistance = 0.1), "core-tactical-planner")

    lazy val forwarderRef = new ForwarderLazyRef(forwarder)
    
    lazy val navigationFeed = Emulator.NavdataDemoFeed
    def navigationFeedReader = Emulator.navdataDemoReaderProps(emulationSim)

    lazy val poseEstimationFeed = ByMeanVelocityNavdataDemoPoseEstimationFeed
    def poseEstimator = ByMeanVelocityNavdataDemoPoseEstimator
      .props(navigationFeed, poseEstimationFeed, env.zero, forwarderRef, startup = 30 millis, stop = 30 millis)
  } 
}

object EmulatorTestApp extends App{
  implicit val asys = ActorSystem.create()
  val core = new EmulatorTest.Core

  core.start()
}
