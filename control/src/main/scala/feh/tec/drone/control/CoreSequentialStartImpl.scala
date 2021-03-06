package feh.tec.drone.control

import akka.actor.{Actor, ActorRef}
import akka.event.Logging
import feh.tec.matlab.server.MatlabAsyncServer
import scala.util._
import feh.tec.drone.control.LifetimeController._
import feh.util._
import akka.pattern.ask
import feh.tec.matlab.MatlabSimClient
import feh.tec.drone.control.LifetimeController.StartupException
import feh.tec.drone.control.LifetimeController.Stage
import scala.concurrent.duration._
import scala.util.Failure
import feh.tec.drone.control.LifetimeController.StartupException
import scala.util.Success
import feh.tec.drone.control.LifetimeController.Stage
import scala.concurrent.{Future, ExecutionContext}
import feh.tec.drone.control.TacticalPlanner.SetWaypoints

/**
 * loses generality on control, for usage with matlab simulation and control
 */
trait CoreSequentialStartImpl extends Core{
  self: MatlabEmulationCore with MatlabControlCore =>

  // CoreSequentialStart.StartupController
  protected val lifetimeController: ActorRef

  def serviceExecContext: ExecutionContext

  def stages: Seq[Stage] = {
    implicit def ec = serviceExecContext
    Seq(
      Stage("controller emulator", () => controller.ask(Control.Start)(emulationConfig.simStartTimeout).mapTo[Try[Any]].map(_.get)),
      Stage("forwarder", () => forwarder.ask(Control.Start)(emulationConfig.simStartTimeout).mapTo[Try[Any]].map(_.get)),
      Stage("matlab tactical control", () => (tacticalPlanner ? Control.Start)(controlConfig.simStartTimeout).mapTo[Try[Any]].map(_.get)),
      Stage("set waypoint", () => Future.successful{ tacticalPlanner ! SetWaypoints((4, 4, -4)) })
//      Stage("start trajectory execution by TacticalPlanner", () =>
//        (tacticalPlanner ? StraightLineTacticalPlanner.SendCommand)(controlConfig.defaultTimeout))
    )
  }

  override def start(){
    lifetimeController ! Control.Start
  }

  override def stop() = {
    implicit def ec = serviceExecContext
    implicit def t = controlConfig.simStopTimeout

    Future.sequence(Seq(
      tacticalPlanner ? Control.Stop,
      controller ? Control.Stop,
      forwarder ? Control.Stop
    )).onComplete(_.get)
  }
}

object CoreSequentialStartImpl{

  case object NextStage

  trait StartupController extends LifetimeController.StartupController with SimulationErrorListener{
    protected val log = Logging(context.system, this)

    val asys = context.system
    import asys._

    var currentStage: Option[Stage] = None
    var stagesToGo = startupStages
    def nextStage() = {
      val s = stagesToGo.headOption.map{
        h =>
          stagesToGo = stagesToGo.tail
          h
      }
      if(s.isEmpty) startupTerminated()
      currentStage = s
      s
    }
    def startupTerminated(){
      log.info("initiation finished")
      state = State.Running
//      simulations.foreach(_.unsubscribeOnErrors(self))
//      context.system.stop(self)
    }

    var errors: Seq[Throwable] = Nil
    def errorsHappened = errors.nonEmpty

    override def receive = ({
      case Control.Start if state == State.Uninitialized =>
        simulations.foreach(_.subscribeOnErrors(self))
        state = State.StartingUp
        self ! NextStage
      case Control.Stop if state == State.StartingUp => errors :+= new Exception("Stopped by " + sender)
      case err: MatlabAsyncServer.Error if state == State.StartingUp =>
        log.error("on startup: " + err)
        errors :+= err
      case ex@RunException(_, _, thr) =>
        log.error(ex.toString)
        thr.map(log.error(_, "RunException"))
      case ex: LifetimeException => log.error(ex.toString)
      case NextStage if state == State.StartingUp =>
        if(errorsHappened) {
          val errMsg = StartupException(errors, currentStage.get)
          log.error(errMsg.toString)
          throw errMsg
        }
        else
          nextStage().map{_
            .$$(s => log.info("initiating " + s))
            .exec()
            .onComplete{
            case Success(_) =>
              log.info(currentStage.get + " initialized")
              self ! NextStage
            case Failure(err) =>
              errors :+= err
              self ! NextStage
          }

          }
    }: Actor.Receive) orElse super.receive
  }
  
  class LifeController(val startupStages: Seq[Stage], val simulations: Seq[MatlabSimClient]) 
    extends LifetimeControllerBase with StartupController
  {
    override protected def stateChanged(old: State.Value, n: State.Value){
      log.info("New state: " + n)
      super.stateChanged(old, n)
    }

    def simulationError(sim: MatlabSimClient, err: Throwable){
      log.error("error in sim " + sim.simName(10 millis) + ": " + err)
      throw err
    }
  }
}
