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

  class Core(implicit actorSys: ActorSystem) extends CoreBase(
    env = new SimpleEnvironment(),
    controllerProps = null,
    forwarderProps = params => 
      DataForwarder.props(new EmulatorFeedChannelStub, _ => None,
        aref => params.feedReaders, params.feedNotifiers, readFreq)(actorSys)
  ) with NavigationCore with MatlabControlCore with MatlabEmulationCore with CoreSequentialStartImpl
  {

    def startupExecContext = asys.dispatcher
    import asys.dispatcher

    def simulations = controlMatlab :: emulationMatlab :: Nil

    protected val lifetimeController: ActorRef =
      asys.actorOf(Props(classOf[CoreSequentialStartImpl.LifeController], stages, simulations), "startup-controller")

    lazy val controlMatlab = new MatlabSimClient(asys.actorSelection(server.DynControl.path))
    lazy val controlConfig = SimConfig(defaultTimeout = 50 millis, simStartTimeout = 30 seconds, execContext = asys.dispatcher)

    lazy val emulationMatlab = new MatlabSimClient(asys.actorSelection(server.DroneEmul.path))
    lazy val emulationModel = new DroneModel
    lazy val emulationConfig = SimConfig(defaultTimeout = 20 millis, simStartTimeout = 30 seconds, execContext = asys.dispatcher)
    lazy val emulationSim =
      new DroneSimulation[Emulator.Model](emulationModel, emulationMatlab, emulationConfig.defaultTimeout)

    override lazy val controller = asys.actorOf(Emulator.controllerProps(emulationSim, emulationConfig.simStartTimeout))

    def tacticalPlanner = asys.actorOf(StraightLineTacticalPlanner
      .props(env, controller, forwarder, controlMatlab, controlConfig, navigationFeed, poseEstimationFeed,
        pointDistance = 0.1))

    lazy val forwarderRef = new ForwarderLazyRef(forwarder)
    
    def navigationFeed = Emulator.NavdataDemoFeed
    def navigationFeedReader = Emulator.navdataDemoReaderProps(emulationSim)

    def poseEstimationFeed = ByMeanVelocityNavdataDemoPoseEstimationFeed
    def poseEstimator = ByMeanVelocityNavdataDemoPoseEstimator
      .props(navigationFeed, poseEstimationFeed, env.zero, forwarderRef, startup = 30 millis)
  } 
}

object EmulatorTestApp extends App{
  implicit val asys = ActorSystem.create()
  val core = new EmulatorTest.Core

  core.start()
}
