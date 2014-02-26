package feh.tec.matlab.server

import akka.actor.{ActorRef, Props, ActorSystem, Actor}
import com.mathworks.jmi.{NativeMatlab, Matlab}
import feh.tec.matlab.server.MatlabServer.ServerActor
import feh.util._
import akka.event.{LoggingAdapter, Logging}
import java.util.UUID
import scala.collection.mutable

class MatlabServer(name: String)(implicit val system: ActorSystem) {
  val actor = system.actorOf(Props(classOf[ServerActor], shutdown.lifted), name)

  def shutdown() {
    system.shutdown()
    sys.exit()
  }
}

object MatlabServer{
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
        val id = UUID.randomUUID()
        requests += id -> sender
        withinMatlabThread(id, Matlab.mtEval(s, n))
      case Feval(fn, args, n) =>
        val id = UUID.randomUUID()
        requests += id -> sender
        withinMatlabThread(id, Matlab.mtFeval(fn, args.map(_.asInstanceOf[AnyRef]).toArray, n))
      case Shutdown => shutdown()
      case SResult(id, res) => requests.remove(id).get ! res
    }

    def withinMatlabThread(id: UUID, f: => Any) = Matlab.whenMatlabIdle(new Runnable {
      def run() =
       (try Result(f.asInstanceOf[Array[Any]])
        catch {
          case ex: Throwable =>
            log.error(ex, "server call: error")
            Error(ex)
        }) |> {
         self ! SResult(id, _)
       }
    })
  }
}