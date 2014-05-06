package feh.tec.matlab.server

import akka.actor._
import com.mathworks.jmi.Matlab
import feh.util._
import akka.event.Logging
import java.util.UUID
import scala.collection.mutable
import akka.actor.ActorDSL._
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.Some
import akka.remote.RemoteScope
import feh.tec.matlab.server.MatlabQueueServer.SimStarted

trait MatlabServer{
  def name: String
  def serverActor: ActorRef
  def shutdown()
}


class MatlabAsyncServer(val name: String)(implicit val asystem: ActorSystem) extends MatlabServer{
  def serverActorProps = Props(classOf[MatlabAsyncServer.ServerActor], shutdown.lifted)
  val serverActor = asystem.actorOf(serverActorProps, name)

  def shutdown() {
    asystem.shutdown()
    sys.exit()
  }
}

object MatlabAsyncServer{
  case class Eval(str: String, nRes: Int)
  case class Feval(fname: String, args: List[Any], nRes: Int)
  case object Shutdown

  case class Result(value: Any)
  case class Error(thr: Throwable)

  class ServerActor(shutdown: () => Unit) extends Actor{
    val log = Logging.getLogger(context.system, this)

    case class SResult(id: UUID, res: Any)

    val requests = mutable.HashMap.empty[UUID, ActorRef]

    def receive = {
      case Eval(s, n) =>
        log.debug("Eval")
        guardRequestAndExec(Matlab.mtEval(s, n))
      case Feval(fn, args, n) =>
        log.debug("Feval")
        guardRequestAndExec(Matlab.mtFeval(fn, args.map(_.asInstanceOf[AnyRef]).toArray, n))
      case Shutdown => shutdown()
      case SResult(id, res) =>
        log.debug("Result")
        requests.remove(id).get ! res
    }

    def guardRequestAndExec(f: => Any, id: UUID = UUID.randomUUID()) = {
      requests += id -> sender()
      withinMatlabThread(id, f)
    }

    def withinMatlabThread(id: UUID, f: => Any) = Matlab.whenMatlabReady(new Runnable {
      log.debug("MatlabThread Runnable created")
      def run() = execAndRespond(id, f)
    })

    def execAndRespond(id: UUID, f: => Any) =
      (try Result(f)
      catch {
        case ex: Throwable =>
          log.error(ex, "server call: error")
          Error(ex)
      }) |> { r =>
        log.debug("res")
        self ! SResult(id, r)
      }

  }

}


/** Executes next queued statement at `execNext` call.
  * Is used in a simulink block callback to allow access to matlab thread
  */
class MatlabQueueServer(name: String)(implicit asystem: ActorSystem) extends MatlabAsyncServer(name){
  override def serverActorProps = Props(classOf[MatlabQueueServer.ServerActor], shutdown.lifted, queue.put _, queue.clear _)

  object queue{

    case class Put(ex: Lifted[Any])
    case object Next{
      implicit def nextToOpt(next: Next): Option[Lifted[Any]] = next.opt
    }
    case class Next(opt: Option[Lifted[Any]])

    case object Clear

    case object List

    val queueManager = actor("AQ")(new Act {
      val queue = mutable.Queue.empty[Lifted[Any]]

      become{
        case Put(x) => queue += x
        case Next => sender ! Next(if(queue.nonEmpty) Some(queue.dequeue()) else None)
        case Clear => queue.clear()
        case List => sender ! queue.toList
      }
    })(implicitly, asystem)


    def put(ex: Lifted[Any]) = queueManager ! Put(ex)
    def next() = Await.result((queueManager ? Next)(10 millis).mapTo[Next], 10 millis)

    def list() = Await.result((queueManager ? List)(10 millis).mapTo[List[Lifted[Any]]], 10 millis)

    def clear() = queueManager ! Clear
  }

  def execNext() = queue.next().foreach(f =>{
    asystem.log.debug("Executing next")
    f()
    asystem.log.debug("Executed next")
  })

  def simStarted() = {
    serverActor ! SimStarted
    sys.error("ASDFAXDASD")
  }
}

/*
object RemoteMatlabQueueServer{
  def apply(name: String, host: String, port: Int)(implicit _asystem: ActorSystem) =
    new MatlabQueueServer(name){
      override def serverActorProps: Props = super.serverActorProps.withDeploy(Deploy(
        scope = RemoteScope(Address("akka.tcp", "sys", host, port))
      ))
    }
}
*/

object MatlabQueueServer{

  case class StartSim(simName: String, execLoopBlock: String)
  case object StopSim

  case object SimStopped

  case object SimStarted

  case object InSim

  class ServerActor(shutdown: () => Unit, putToQueue: Lifted[Any] => Unit, clearQueue: () => Unit)
    extends MatlabAsyncServer.ServerActor(shutdown)
  {

    def startSim(name: String, block: String) = {
      Matlab.mtEval(name)
      Matlab.mtFeval("setSimOnStart", Array(name, block), 0)
      Matlab.mtFeval("sim", Array(name), 1)
    }
    def stopSim(name: String) = {
      Matlab.mtFeval("set_param", Array(name, "SimulationCommand", "stop"), 0)
      Matlab.mtFeval("close_system", Array(name), 0)
    }

    var simStartRequester: Option[ActorRef] = None

    def respondToSimRequester(msg: Any) = simStartRequester.foreach{
      s =>
        s ! msg
        simStartRequester = None
    }

    override def receive = super.receive orElse {
      case StartSim(name, block) =>
        log.info("Start Sim")
        simStartRequester = Some(sender())
        guardRequestAndExec(
          try startSim(name, block)
          catch {
            case err: Throwable =>
              log.error("error on sim start, requester = " + simStartRequester)
              respondToSimRequester(MatlabAsyncServer.Error(err))
          }
        )
        guardRequestAndExec({
          log.info("notifying simulation finished")
          self ! SimStopped
          clearQueue()
        }
        )
        simName = Some(name)
      case StopSim if inSim =>
        log.info("Stop Sim")
        val name = simName.get
        simName = None
        stopSim(name)
      case SimStarted =>
        log.info("Sim Started, requester = " + simStartRequester)
        respondToSimRequester(SimStarted)
      case SimStopped =>
        log.info("Sim Stopped")
        simName = None
      case InSim => sender ! inSim
    }

    var simName: Option[String] = None
    def inSim = simName.isDefined

    override def withinMatlabThread(id: UUID, f: => Any) ={
      log.debug(s"inSim = $inSim")
      if(inSim) putToQueue(() => execAndRespond(id, f))
      else super.withinMatlabThread(id, f)
    }
  }
}
