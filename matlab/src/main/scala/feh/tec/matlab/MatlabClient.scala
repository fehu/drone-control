package feh.tec.matlab

import akka.actor.{ActorRef, ActorSystem, ActorSelection}
import feh.tec.matlab.server.{MatlabAsyncServer, MatlabQueueServer}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.{ExecutionContext, Future}
import feh.util._
import akka.actor.ActorDSL._
import feh.tec.matlab.server.MatlabQueueServer.{SimStarted, UnsubscribeErrors, SubscribeErrors}
import scala.util.{Success, Failure, Try}

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
    case ex: MatlabAsyncServer.Error => throw ex
  }
}

class MatlabSimClient(server: ActorSelection)(implicit _asys: ActorSystem) extends MatlabClient(server){
  import asys._

  def startSim(name: String, execLoopBlock: String)(implicit timeout: Timeout) =
    server ? MatlabQueueServer.StartSim(name, execLoopBlock) map {
      case SimStarted => Success(SimStarted)
      case err: MatlabAsyncServer.Error => Failure(err)
    }

  def stopSim(name: String)(implicit timeout: Timeout) =
    server ? MatlabQueueServer.StopSim |> parseResult

  def inSim(implicit timeout: Timeout) = (server ? MatlabQueueServer.InSim).mapTo[Boolean]
  def simName(implicit timeout: Timeout) = (server ? MatlabQueueServer.SimName).mapTo[Option[String]]

  def subscribeOnErrors(ref: ActorRef) = (server ! SubscribeErrors)(ref)
  def unsubscribeOnErrors(ref: ActorRef) = (server ! UnsubscribeErrors)(ref)
}