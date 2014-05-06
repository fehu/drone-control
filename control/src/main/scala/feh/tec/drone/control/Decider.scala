package feh.tec.drone.control

import akka.actor.{ActorRef, Actor}
import akka.pattern.ask
import feh.tec.drone.control.DataForwarder.Forward
import feh.tec.drone.emul.Emulator
import akka.util.Timeout
import scala.concurrent.ExecutionContext

/** Decides what action should the drone perform
 */
trait Decider extends Actor{
  def controller: ActorRef
}

/*
@deprecated("delete it")
class PositionKeeperDemo(val controller: ActorRef, controlTimeout: Timeout)
                        (implicit execContext: ExecutionContext) extends Decider{
  decider =>

  val env = new SimpleEnvironment
  val positionNotifier =
    new ByNavdataDemoDiffPoseEstimator(Emulator.NavdataDemoFeed, env.zero)
      with ByMeanVelocityNavdataDemoPoseEstimator
      with PoseNotifier[NavdataDemo]
    {
      def forwarder = decider.self

      lazy val NavdataDemoFeed = BuildDataMatcher[NavdataDemoFeed]

      def buildData = {
        case NavdataDemoFeed(data) => data
      }

      var on_? = false

      def start() = { on_? = true }
      def stop() = { on_? = false }
    }

  def receive = {
    case Control.Start =>
      implicit def timeout = controlTimeout
      controller ? Control.Start map {
        case Control.Success =>
          positionNotifier.start()
        case Control.Error(err) => sys.error("failed to start controller: " + err.getMessage)
      }
    case Control.Stop =>
      implicit def timeout = controlTimeout
      controller ? Control.Stop
//    case PoseEstimated(pos) =>
//      env.dronePosition = pos.position
//      println(s"position: $pos")
  }
}*/
