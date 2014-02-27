package feh.tec.matlab

import akka.actor.{ActorSelection, ActorRef}
import feh.tec.matlab.server.MatlabServer
import akka.pattern.ask
import akka.util.Timeout
import feh.tec.matlab.server.MatlabServer.Result
import scala.concurrent.{ExecutionContext, Future}
import feh.util._

class MatlabClient(val server: ActorSelection)(implicit val execContext: ExecutionContext) {

  def shutdownServer() = server ! MatlabServer.Shutdown

  def eval(expr: String, nReturn: Int)(implicit timeout: Timeout) =
    server ? MatlabServer.Eval(expr, nReturn) |> parseResult

  def feval(fname: String, args: List[Any], nReturn: Int)(implicit timeout: Timeout) =
    server ? MatlabServer.Feval(fname, args, nReturn) |> parseResult

  def startSim(name: String)(implicit timeout: Timeout) =
    server ? MatlabServer.StartSim(name) |> parseResult

  protected def parseResult: Future[Any] => Future[Array[Any]] = _.map{
    case r: Result => r.arr
    case MatlabServer.Error(ex) => throw ex
  }
}
