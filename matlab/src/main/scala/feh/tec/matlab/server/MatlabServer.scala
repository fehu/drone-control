package feh.tec.matlab.server

import akka.actor.{ActorRef, Props, ActorSystem, Actor}
import com.mathworks.jmi.{NativeMatlab, Matlab}
import feh.tec.matlab.server.MatlabServer.{ServerActor}
import feh.util._
import akka.event.{LoggingAdapter, Logging}
import java.util.UUID
import scala.collection.mutable
import akka.actor.ActorDSL._
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.Await

class MatlabServer(name: String)(implicit val asystem: ActorSystem) {
  val serverActor = asystem.actorOf(Props(classOf[ServerActor], shutdown.lifted, queue.put _), name)

  def shutdown() {
    asystem.shutdown()
    sys.exit()
  }
  
  object queue{
    import Default._

    case class Put(ex: Lifted[Any])
    case object Next{
      implicit def nextToOpt(next: Next): Option[Lifted[Any]] = next.opt
    }
    case class Next(opt: Option[Lifted[Any]])

    import asystem._

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
    asystem.log.info("Executing next")
    f()
    asystem.log.info("Executed next")
  })
}

object MatlabServer{
  case class Eval(str: String, nRes: Int)
  case class Feval(fname: String, args: List[Any], nRes: Int)
  case object Shutdown

  case class StartSim(simName: String)

  case class Result(arr: Array[Any])
  case class Error(thr: Throwable)

  class ServerActor(shutdown: () => Unit, putToQueue: Lifted[Any] => Unit) extends Actor{
    val log = Logging.getLogger(context.system, this)

    case class SResult(id: UUID, res: Any)

    val requests = mutable.HashMap.empty[UUID, ActorRef]

    var inSim = false

    def receive = {
      case Eval(s, n) =>
        log.info("Eval")
        val id = UUID.randomUUID()
        requests += id -> sender
        withinMatlabThread(id, Matlab.mtEval(s, n))
      case Feval(fn, args, n) =>
        log.info("Feval")
        val id = UUID.randomUUID()
        requests += id -> sender
        withinMatlabThread(id, Matlab.mtFeval(fn, args.map(_.asInstanceOf[AnyRef]).toArray, n))
      case StartSim(name) =>
        val id = UUID.randomUUID()
        requests += id -> sender
        val callLoop = "feh.tec.matlab.server.LoopHolder.get().loopExec()"
        withinMatlabThread(id, Matlab.mtFeval("simParExec", Array(name, callLoop), 0))
//        val feval = new Runnable { def run() = Matlab.mtFeval(fn, args.map(_.asInstanceOf[AnyRef]).toArray, n) }
//        val exec = new Runnable {
//          def run() = {
//            usingLoop = true
//            loop.execLoop()
//          }
//        }
//        withinMatlabThread(id, Matlab.mtFeval("parExec", Array(Array(feval, exec)), 0))
      case Shutdown => shutdown()
      case SResult(id, res) =>
        log.info("Result")
        requests.remove(id).get ! res
    }

    def withinMatlabThread(id: UUID, f: => Any) = putToQueue(() => execAndRespond(id, f))
//      if(inSim) Matlab.
//        //loop.inMThread(execAndRespond(id, f).lifted)
//      else Matlab.whenMatlabIdle(new Runnable { def run() = execAndRespond(id, f) })

    def execAndRespond(id: UUID, f: => Any) =
      (try Result(f.asInstanceOf[Array[Any]])
      catch {
        case ex: Throwable =>
          log.error(ex, "server call: error")
          Error(ex)
      }) |> { r =>
        log.info("res")
        self ! SResult(id, r)
      }

  }

/*
  class InLoopExecutor{
    protected var executing = false
    protected var exec: Option[Lifted[Any]] = None

    def inMThread(f: () => Any){
      exec = Some(f)
    }

    def isMReady = !executing

    def loopWaitTime = 10
    def execLoop(){
      exec.synchronized{
        exec.foreach(f => {
          executing = true
          println("executing " + NativeMatlab.nativeIsMatlabThread())
          f()
          //      Matlab.whenMatlabReady(new Runnable { def run() = f() })
        })
      }
      if(executing){
        executing = false
        exec = None
      }
      Thread.sleep(loopWaitTime)
      execLoop()
    }

  }
*/

}