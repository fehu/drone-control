package feh.tec.matlab

import akka.actor.{ActorSystem, ActorSelection}
import feh.tec.matlab.server.{MatlabAsyncServer, MatlabQueueServer}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.{ExecutionContext, Future}
import feh.util._
import akka.actor.ActorDSL._

class MatlabClient(val server: ActorSelection)(implicit val asys: ActorSystem) {
  import asys._

  def shutdownServer() = server ! MatlabAsyncServer.Shutdown

  def eval(expr: String, nReturn: Int)(implicit timeout: Timeout) =
    server ? MatlabAsyncServer.Eval(expr, nReturn) |> parseResult

  def feval(fname: String, args: List[Any], nReturn: Int)(implicit timeout: Timeout) =
    server ? MatlabAsyncServer.Feval(fname, args, nReturn) |> parseResult

  protected def parseResult: Future[Any] => Future[Array[Any]] = _.map{
    case r: MatlabAsyncServer.Result => r.value match {
      case arr: Array[Any] => arr
      case any => Array(any)
    }
    case MatlabAsyncServer.Error(ex) => throw ex
  }
}

class MatlabSimClient(server: ActorSelection)(implicit asys: ActorSystem) extends MatlabClient(server){
  def startSim(name: String, execLoopBlock: String)(implicit timeout: Timeout) =
    server ? MatlabQueueServer.StartSim(name, execLoopBlock) |> parseResult

  def stopSim(name: String)(implicit timeout: Timeout) =
    server ? MatlabQueueServer.StopSim |> parseResult

  def inSim(implicit timeout: Timeout) = (server ? MatlabQueueServer.InSim).mapTo[Boolean]
}