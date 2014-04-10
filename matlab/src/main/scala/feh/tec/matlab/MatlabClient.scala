package feh.tec.matlab

import akka.actor.ActorSelection
import feh.tec.matlab.server.{MatlabAsyncServer, MatlabQueueServer}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.{ExecutionContext, Future}
import feh.util._

class MatlabClient(val server: ActorSelection)(implicit val execContext: ExecutionContext) {

  def shutdownServer() = server ! MatlabAsyncServer.Shutdown

  def eval(expr: String, nReturn: Int)(implicit timeout: Timeout) =
    server ? MatlabAsyncServer.Eval(expr, nReturn) |> parseResult

  def feval(fname: String, args: List[Any], nReturn: Int)(implicit timeout: Timeout) =
    server ? MatlabAsyncServer.Feval(fname, args, nReturn) |> parseResult

  protected def parseResult: Future[Any] => Future[Array[Any]] = _.map{
    case r: MatlabAsyncServer.Result => r.arr
    case MatlabAsyncServer.Error(ex) => throw ex
  }
}

class MatlabSimClient(server: ActorSelection)(implicit execContext: ExecutionContext) extends MatlabClient(server){
  def startSim(name: String, execLoopBlock: String)(implicit timeout: Timeout) =
    server ? MatlabQueueServer.StartSim(name, execLoopBlock) |> parseResult

  def stopSim(name: String)(implicit timeout: Timeout) =
    server ? MatlabQueueServer.StopSim |> parseResult
}