package feh.tec.drone.control

import feh.tec.drone.control.emul.{DroneModel, EmulatorFeedChannelStub, Emulator}
import scala.concurrent.duration._
import feh.tec.matlab.{DroneSimulation, MatlabSimClient}
import feh.tec.drone.control.Config.SimConfig
import akka.actor.ActorSystem


object EmulatorTest {
  val actorSys = ActorSystem.create()

  val readFreq: FiniteDuration = 30 millis span

  class Core extends CoreBase(
    env = new SimpleEnvironment(),
    controllerProps = null,
    forwarderProps = params => 
      DataForwarder.props(new EmulatorFeedChannelStub, _ => None,
        aref => params.feedReaders.mapValues(_.props), params.feedNotifiers, readFreq)(actorSys)
  )(actorSys) with NavigationCore with MatlabControlCore with MatlabEmulationCore
  {

    import asys.dispatcher

    val controlMatlab = new MatlabSimClient(null) // todo
    val controlConfig = SimConfig(defaultTimeout = 20 millis, simStartTimeout = 30 seconds, execContext = asys.dispatcher)

    val emulationMatlab = new MatlabSimClient(null) // todo
    val emulationModel = new DroneModel
    val emulationConfig = SimConfig(defaultTimeout = 20 millis, simStartTimeout = 30 seconds, execContext = asys.dispatcher)
    val emulationSim =
      new DroneSimulation[Emulator.Model](emulationModel, emulationMatlab, emulationConfig.defaultTimeout)

    override lazy val controller = asys.actorOf(Emulator.controllerProps(emulationSim, emulationConfig.simStartTimeout))

    def tacticalPlanner = asys.actorOf(StraightLineTacticalPlanner
      .props(env, controlMatlab, controlConfig, ByMeanVelocityNavdataDemoPoseEstimationFeed.tag,
        pointDistance = 0.1))

    def navigationFeed = Emulator.NavdataDemoFeed
    def navigationFeedReader = Emulator.navdataDemoReaderProps(emulationSim, forwarder)

    def poseEstimationFeed = ByMeanVelocityNavdataDemoPoseEstimationFeed
    def poseEstimator = ByMeanVelocityNavdataDemoPoseEstimator
      .props(navigationFeed, env.zero, forwarder)
  } 
}
