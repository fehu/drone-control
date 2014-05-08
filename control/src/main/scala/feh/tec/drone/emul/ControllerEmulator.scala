package feh.tec.drone.emul

import akka.actor.{ActorRef, Props, ActorSystem}
import feh.tec.matlab._
import scala.concurrent.Await
import feh.tec.drone.control.DroneApiCommands._
import akka.util.Timeout
import feh.tec.drone.control._
import feh.tec.drone.control.DroneApiCommands.Move
import scala.reflect.runtime.universe._
import akka.event.Logging

class ControllerEmulator(val simulator: DroneSimulation[Emulator.Model],
                         startTimeout: Timeout,
                         implicit val asys: ActorSystem) extends Controller{

  import asys._

  val ioControl = null

  def watchdog = ???

  protected val log = Logging(context.system, this)

  def receive = {
    case control: Control.Message => msgControl(control)
    case req: Controller.Req => msgReq(req)
    case command: ControlCommand => msgCommand(command)
  }

  def msgControl: PartialFunction[Control.Message, Unit] = {
    case Control.Start =>
      simulator.start(startTimeout).onComplete(sender !)
    case Control.Stop =>
      log.info("stopping drone simulator")
      simulator.stop.map{ _ =>
        log.info("drone simulator stopped")

      } onComplete (sender !)
  }
  
  def msgReq: PartialFunction[Controller.Req, Unit] = Map()
  
  def msgCommand: PartialFunction[ControlCommand, Unit] = {
    case Takeoff => ???
    case Land => ???
    case EmergencyStop => simulator.setParam(_.gaz, 0d)
    case CancelEmergencyStop => ???
    case m@Move(_, roll, pitch, yaw, gaz) =>
      log.info("ControllerEmulator: received move command: " + m)
      simulator.setParam(_.roll, roll)
      simulator.setParam(_.pitch, pitch)
      simulator.setParam(_.yaw, yaw) // todo
      simulator.setParam(_.gaz, gaz)
  }
}

sealed trait EmulatorIOChannel extends IOChannel

class EmulatorFeedChannelStub extends EmulatorIOChannel with IOFeedChannel{
  def connected_? = true
  def read() = None
}

object Emulator{
  type Model = DroneModel

  object NavdataDemoFeed extends NavdataDemoFeed{
    def parseData = sys.error("use EmulatorFeedChannel's `data` method")
    def dataTag = typeTag[NavdataDemo]
    def name = "NavdataDemo"
  }

  protected def fetchNavdata(sim: DroneSimulation[Emulator.Model]) =
    Await.result(sim.execMethod(_.navdataDemo), sim.defaultTimeout.duration)

  def navdataDemoReaderProps(sim: DroneSimulation[Emulator.Model]) =
    FeedReader.generic[NavdataDemoFeed.type, EmulatorFeedChannelStub](NavdataDemoFeed,
      _ => Some(fetchNavdata(sim)) //Array[Byte] => Option[NavdataDemoFeed.Data]
    )

  def controllerProps(simulator: DroneSimulation[Model],
                      startTimeout: Timeout)
                     (implicit system: ActorSystem) =
    Props(classOf[ControllerEmulator], simulator, startTimeout, system)
}

/*
object Test{
  val defaultTimeout = 10 millis span
  val gazCoeff = 50
  val controlTimeout = 10 millis span

  val matlab = new MatlabSimClient(server.Default.sel)(server.Default.system.dispatcher)

  implicit val system = ActorSystem.create()
  import system._

  val simulator = new DroneSimulation[DroneModel](new DroneModel, matlab, defaultTimeout)

  def readers(forwarder: ActorRef): Map[DataFeed, Props] = Map(Emulator.NavdataDemoFeed -> Emulator.navdataDemoReaderProps(simulator, forwarder))
  def bForwarder(controller: ActorRef) = DataForwarder.props(controller, new EmulatorFeedChannelStub, readers, controlTimeout)

  val controller = system.actorOf(Emulator.controllerProps(simulator, bForwarder))
  val forwarder = Await.result(
    (controller ? Controller.GetForwarder)(10 millis).mapTo[Controller.ForwarderRef].map(_.ref),
    10 millis
  )
  
  val decider = ???
}
*/

