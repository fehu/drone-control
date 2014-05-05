package feh.tec.drone.control.emul

import akka.actor.{ActorRef, Props, ActorSystem}
import feh.tec.matlab._
import scala.concurrent.Await
import feh.tec.drone.control.DroneApiCommands._
import akka.util.Timeout
import feh.tec.drone.control._
import scala.concurrent.duration._
import feh.tec.drone.control.DroneApiCommands.Move
import akka.pattern.ask
import scala.reflect.runtime.universe._

class ControllerEmulator(val simulator: DroneSimulation[Emulator.Model],
                         bForwarder: ActorRef => Props,
                        implicit val asys: ActorSystem) extends Controller{
  val ioControl = null

  val forwarder = asys.actorOf(bForwarder(self))

  def watchdog = ???

  def receive = {
    case control: Control.Message => msgControl(control)
    case req: Controller.Req => msgReq(req)
    case command: ControlCommand => msgCommand(command)
  }

  def msgControl: PartialFunction[Control.Message, Unit] = {
    case Control.Start =>
      forwarder ! Control.Start

    case Control.Stop =>
      forwarder ! Control.Stop
  }
  
  def msgReq: PartialFunction[Controller.Req, Unit] = {
    case Controller.GetForwarder => Controller.ForwarderRef(forwarder) 
  }
  
  def msgCommand: PartialFunction[ControlCommand, Unit] = {
    case Takeoff => ???
    case Land => ???
    case EmergencyStop => simulator.setParam(_.gaz, 0d)
    case CancelEmergencyStop => ???
    case Move(_, roll, pitch, yaw, gaz) =>
      simulator.setParam(_.roll, roll)
      simulator.setParam(_.pitch, pitch)
      simulator.setParam(_.yaw, yaw.d) // todo
      simulator.setParam(_.gaz, gaz.d)
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
  }
  def navdataDemoReaderProps(sim: DroneSimulation[Emulator.Model], forwarder: ActorRef) =
    FeedReader.generic[NavdataDemoFeed.type, EmulatorFeedChannelStub](NavdataDemoFeed, forwarder,
      _ => Some(Await.result(sim.execMethod(_.navdataDemo), sim.defaultTimeout.duration)) //Array[Byte] => Option[NavdataDemoFeed.Data]
    )

  def controllerProps(simulator: DroneSimulation[Model],
                      bForwarder: ActorRef => Props)
                     (implicit system: ActorSystem) =
    Props(classOf[ControllerEmulator], simulator, bForwarder, system)
}

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

