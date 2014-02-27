package feh.tec.matlab.server

import akka.actor.{ActorRef, Props, ActorSystem, Actor}
import com.mathworks.jmi.Matlab
import feh.util._
import akka.event.Logging
import java.util.UUID
import scala.collection.mutable
import akka.actor.ActorDSL._
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.Await

trait MatlabServer{
  def name: String
  def serverActor: ActorRef
  def shutdown()
}


class MatlabAsyncServer(val name: String)(implicit val asystem: ActorSystem) extends MatlabServer{
  // it's lazy only because of a ?bug? causing overridden vals instances creation
  lazy val serverActor = asystem.actorOf(Props(classOf[MatlabAsyncServer.ServerActor], shutdown.lifted), name)

  def shutdown() {
    asystem.shutdown()
    sys.exit()
  }

  serverActor
}

object MatlabAsyncServer{
  case class Eval(str: String, nRes: Int)
  case class Feval(fname: String, args: List[Any], nRes: Int)
  case object Shutdown

  case class Result(arr: Array[Any])
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
      requests += id -> sender
      withinMatlabThread(id, f)
    }

    def withinMatlabThread(id: UUID, f: => Any) = Matlab.whenMatlabReady(new Runnable {
      log.debug("MatlabThread Runnable created")
      def run() = execAndRespond(id, f)
    })

    def execAndRespond(id: UUID, f: => Any) =
      (try Result(f.asInstanceOf[Array[Any]])
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
class MatlabQueueServer(name: String)(implicit _asystem: ActorSystem) extends MatlabAsyncServer(name){
  override lazy val serverActor = asystem.actorOf(Props(classOf[MatlabQueueServer.ServerActor], shutdown.lifted, queue.put _), name)

  object queue{
    import Default._

    case class Put(ex: Lifted[Any])
    case object Next{
      implicit def nextToOpt(next: Next): Option[Lifted[Any]] = next.opt
    }
    case class Next(opt: Option[Lifted[Any]])


    val queueManager = actor(new Act {
      val queue = mutable.Queue.empty[Lifted[Any]]

      become{
        case Put(x) => queue += x
        case Next => sender ! Next(if(queue.nonEmpty) Some(queue.dequeue()) else None)
      }
    })


    def put(ex: Lifted[Any]) = queueManager ! Put(ex)
    def next() = Await.result((queueManager ? Next)(10 millis).mapTo[Next], 10 millis)
  }
  def execNext() = queue.next().foreach(f =>{
    asystem.log.debug("Executing next")
    f()
    asystem.log.debug("Executed next")
  })
}


object MatlabQueueServer{

  case class StartSim(simName: String)
  case object StopSim

  class ServerActor(shutdown: () => Unit, putToQueue: Lifted[Any] => Unit) extends MatlabAsyncServer.ServerActor(shutdown){

    def startSim(name: String) = {
      Matlab.mtFeval("setSimOnStart", Array(), 0)
      Matlab.mtFeval("sim", Array(name), 1)
    }
    def stopSim(name: String) = Matlab.mtFeval("set_param", Array(name, "SimulationCommand", "stop"), 0)

    override def receive = super.receive orElse {
      case StartSim(name) =>
        log.debug("StartSim")
        guardRequestAndExec(startSim(name))
        simName = Some(name)
      case StopSim if inSim =>
        log.debug("StopSim")
        val name = simName.get
        simName = None
        stopSim(name)
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
