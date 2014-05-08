package feh.tec.matlab.server

import akka.actor._
import com.mathworks.jmi.Matlab
import feh.util._
import akka.event.{LoggingAdapter, Logging}
import java.util.UUID
import scala.collection.mutable
import akka.actor.ActorDSL._
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.Some
import feh.tec.matlab.server.MatlabQueueServer.{SimStopped, SimStarted}
import feh.tec.matlab.server.MatlabAsyncServer.MatRequest

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
  trait MatRequest

  case class Eval(str: String, nRes: Int) extends MatRequest
  case class Feval(fname: String, args: List[Any], nRes: Int) extends MatRequest
  case object Shutdown

  case class Result(value: Any)
  case class Error(thr: Throwable, request: MatRequest, requester: ActorPath) extends Exception(
    s"error on executing $request by $requester ${thr.getMessage}",
    thr
  )

  class ServerActor(shutdown: () => Unit) extends Actor{
    val log = Logging.getLogger(context.system, this)

    case class SResult(id: UUID, res: Any)

    val requests = mutable.HashMap.empty[UUID, ActorRef]

    def receive = {
      case req@Eval(s, n) =>
        log.debug("Eval")
        guardRequestAndExec(Matlab.mtEval(s, n), req)
      case req@Feval(fn, args, n) =>
        log.debug("Feval")
        guardRequestAndExec(Matlab.mtFeval(fn, args.map(_.asInstanceOf[AnyRef]).toArray, n), req)
      case Shutdown => shutdown()
      case SResult(id, res) =>
        log.debug("Result")
        requests.remove(id).get ! res
    }

    def guardRequestAndExec(f: => Any, request: MatRequest, id: UUID = UUID.randomUUID()) = {
      val s = sender()
      requests += id -> s
      withinMatlabThread(id, f)(request, s.path)
    }

    def withinMatlabThread(id: UUID, f: => Any)(request: MatRequest, requester: ActorPath) =
      Matlab.whenMatlabReady(new Runnable {
        log.debug("MatlabThread Runnable created")
        def run() = execAndRespond(id, f)(request, requester)
      })

    def execAndRespond(id: UUID, f: => Any)(request: MatRequest, requester: ActorPath) =
      (try Result(f)
      catch {
        case ex: Throwable =>
          reportError(ex, request, requester)
          Error(ex, request, requester)
      }) |> { r =>
        log.debug("res")
        self ! SResult(id, r)
      }

    def reportError(err: Throwable, request: MatRequest, requester: ActorPath){
      log.error(err, "server call: error")
    }

  }

}


/** Executes next queued statement at `execNext` call.
  * Is used in a simulink block callback to allow access to matlab thread
  */
class MatlabQueueServer(name: String)(implicit asystem: ActorSystem) extends MatlabAsyncServer(name){
  override def serverActorProps = Props(classOf[MatlabQueueServer.ServerActor], shutdown.lifted, queue.list().lifted, queue.queueManager)

  object queue{
    import MatlabQueueServer.Queue._
    val log = Logging(asystem, this.getClass)

    val queueManager = actor("matlab-queue-manager")(new Act {
      val queue = mutable.Queue.empty[Lifted[Any]]

      log.info("queueManager started")

      become{
        case Put(x) =>
          log.debug("put to queue")
          queue += x
        case Next =>
//          log.debug("Next requested: " + queue)
          sender ! Next(
            if(queue.nonEmpty) {
              log.debug("dequeued")
              Some(queue.dequeue())
            }
            else None
        )
        case Clear => queue.clear()
        case List => sender ! queue.toList
        case SimStopped => stopped = true
        case SimStarted => stopped = false
      }
    })(implicitly, asystem)


    def next() = Await.result((queueManager ? Next)(10 millis).mapTo[Next], 10 millis)
    def list() = Await.result((queueManager ? List)(50 millis).mapTo[List[Lifted[Any]]], 50 millis)
  }

  private var stopped = false

  def execNext() = queue.next().map(f =>{
    asystem.log.debug("Executing next")
    f()
    asystem.log.debug("Executed next")
    "executed statement               =================================================================================="
  }) getOrElse (if(stopped) "stopped" else "queue was empty")

  /** Notifies the server that a simulation has started (startFcn hook)
   */
  def simStarted() = {
    serverActor ! SimStarted
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
  object Queue{

    case class Put(ex: Lifted[Any])
    case object Next{
      implicit def nextToOpt(next: Next): Option[Lifted[Any]] = next.opt
    }
    case class Next(opt: Option[Lifted[Any]])

    case object Clear

    case object List
  }

  case class StartSim(simName: String, execLoopBlock: String) extends MatRequest
  case object StopSim extends MatRequest

  case object SimStopped

  case object SimStarted

  case object InSim
  case object SimName

  case object SubscribeErrors
  case object UnsubscribeErrors

  class ServerActor(shutdown: () => Unit, listQueue: () => List[Lifted[Any]], queueManager: ActorRef)
    extends MatlabAsyncServer.ServerActor(shutdown)
  {

    var errorSubscribers: Map[ActorPath, ActorSelection] = Map()

    override def reportError(err: Throwable, request: MatRequest, requester: ActorPath){
      super.reportError(err, request, requester)
      log.info("reporting error to subscribers: " + errorSubscribers.values
        .map(sel => sel.anchorPath + sel.pathString.drop(1)).mkString(", "))
      log.debug("errorSubscribers: " + errorSubscribers)
      errorSubscribers.foreach{ case (_, a) =>
        log.debug(s"sending error to $a: $err")
        a ! MatlabAsyncServer.Error(err, request, requester)
      }
    }

    def startSim(name: String, block: String) = {
      Matlab.mtEval("if gcs, close_system(gcs), end")
      Matlab.mtFeval("open_system", Array(name), 0)
      Matlab.mtFeval("setSimOnStart", Array(name, block), 0)
      Matlab.mtFeval("sim", Array(name), 1)
    }

    def stopSim(name: String) = {
      log.info(s"stopping simulation $name")
      withinMatlabThread(
        UUID.randomUUID(),
        Matlab.mtFeval("set_param", Array(name, "SimulationCommand", "stop"), 0)
      )(StopSim, sender.path)
//      Matlab.mtFeval("close_system", Array(name), 0)
    }

    var simStartRequest: Option[ActorRef] = None

    def respondToSimRequester(msg: Any) = simStartRequest.map{
      req =>
        log.info(s"responding to sim start requester $req: $msg")
        req ! msg
        simStartRequest = None
    }


    def putToQueue(f: Lifted[Any]) = queueManager ! Queue.Put(f)
    def clearQueue() = queueManager ! Queue.Clear

    override def receive = super.receive orElse {
      case SubscribeErrors =>
        val path = sender.path
        log.info("new errors subscriber " + path)
        errorSubscribers += path -> context.system.actorSelection(path)
      case UnsubscribeErrors =>
        errorSubscribers -= sender.path
      case req@StartSim(name, block) =>
        log.info("Start Sim")
        val s = sender()
        simStartRequest = Some(s)
        queueManager ! SimStarted
        guardRequestAndExec(
          try startSim(name, block)
          catch {
            case err: Throwable =>
              log.error("error on sim start, requester = " + simStartRequest)
              respondToSimRequester(MatlabAsyncServer.Error(err, req, s.path))
              throw err
//              reportError(err, req, s.path)// ??
          },
          req
        )
        guardRequestAndExec(
          {
            log.info("notifying simulation finished")
            self ! SimStopped
            val l = listQueue()
            log.debug("queue: " + l)
            clearQueue()
          },
          req
        )
        simStarting(name)
      case StopSim if inSim =>
        log.info("Stop Sim")
//        simName = None
        simName.foreach(stopSim)
      case SimStarted =>
        log.info("Sim Started, requester = " + simStartRequest)
        respondToSimRequester(SimStarted)
      case SimStopped => simStopped()
      case InSim => sender ! inSim
      case SimName => sender ! simName
    }
    
    private var simName: Option[String] = None
    def simStarting(name: String) = {
      log.debug("sim name set: " + name)
      simName = Some(name)
    }
    def simStopped() = {
      log.info("Sim Stopped: " + simName.get)
      simName = None
      queueManager ! SimStopped
    }
    def inSim = simName.isDefined

    override def withinMatlabThread(id: UUID, f: => Any)(request: MatRequest, requester: ActorPath) ={
      log.debug(s"inSim = $inSim")
      if(inSim) putToQueue(() => execAndRespond(id, f)(request, requester))
      else super.withinMatlabThread(id, f)(request, requester)
    }
  }
}
