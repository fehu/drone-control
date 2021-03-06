package feh.tec.drone.control

import akka.actor.ActorRef
import feh.tec.drone.control.util.Math._
import breeze.linalg.DenseVector
import Environment._
import feh.tec.drone.control.DataForwarder.{Subscribe, Forward}
import scala.reflect.runtime.{universe => ru}
import akka.event.Logging
import feh.tec.drone.emul.Emulator.NavdataDemoFeed
import akka.util.Timeout

case class Orientation(pitch: Float, roll: Float, yaw: Float)
case class Pose(position: Environment#Coordinate, orientation: Orientation)

trait PoseEstimator[Nav <: NavigationData] extends DataListener[Nav]{
  def estimatePose(data: Nav): Pose
}

trait NavdataDemoPoseEstimator extends PoseEstimator[NavdataDemo]{
  def lastData: NavdataDemo
  def lastReceived: Long
  def lastPosition: Environment#Coordinate
}

trait PoseEstimationFeed extends AbstractDataFeed[PoseEstimationFeed]{
  type Data = Pose
  def dataTag = ru.typeTag[Pose]
  val feedClass = classOf[PoseEstimationFeed]
}

trait PoseNotifier[Nav <: NavigationData] extends PoseEstimator[Nav] with FeedNotifier{
  type NotifyFeed <: PoseEstimationFeed

  def forwarded(data: Nav) = notifyForwarder(estimatePose(data))
}

trait ByNavdataDemoPoseEstimator extends NavdataDemoPoseEstimator{
  def feed: NavdataDemoFeed
  val feeds = Map(feedsEntry[NavdataDemoFeed](feed, identity))

  def orientation(data: NavdataDemo) = Orientation(data.pitch, data.roll, data.yaw)
}


/** Estimates position change using current navdata, the previous one and time between reception.
  */
abstract class ByNavdataDemoDiffPoseEstimator(val feed: NavdataDemoFeed, envZero: Environment#Coordinate)
  extends ByNavdataDemoPoseEstimator
{
  protected val log = Logging(context.system, this)

  var lastData: NavdataDemo = _
  var lastReceived: Long = _
  var lastPosition: Environment#Coordinate = _
  var isFirst = true

  def estimatePose(data: NavdataDemo) = {
    val t = System.currentTimeMillis()
    if(isFirst){
      lastPosition = envZero
      isFirst = false
    }
    else {
      lastPosition = position(data, t, lastData, lastReceived, lastPosition)
    }
    lastReceived = t
    lastData = data
    val pose = Pose(lastPosition, orientation(data))
    log.debug("Pose estimated: " + pose)
    pose
  }

  def position(current: NavdataDemo, currWhen: Long, old: NavdataDemo, oldWhen: Long, lastPos: Environment#Coordinate): Environment#Coordinate

}

/** Estimates movement using sum of previous and current velocity vectors.
  *  Uses messages delay as time to calculate distance travelled.
  */
trait ByMeanVelocityNavdataDemoPoseEstimator extends ByNavdataDemoDiffPoseEstimator
{
  def navdataRTY(nav: NavdataDemo) = rpy(nav.roll, nav.pitch, nav.yaw)
  def velocityVector(nav: NavdataDemo) = DenseVector(nav.vx.toDouble, nav.vy.toDouble, nav.vz.toDouble)//.t
  // todo: in need of jacobian?
  def velocityInBaseFrame(nav: NavdataDemo) = navdataRTY(nav) * velocityVector(nav)

  var lastVelocity: breeze.linalg.Vector[Double] = _

  def position(current: NavdataDemo, currWhen: Long, old: NavdataDemo, oldWhen: Long, lastPos: Environment#Coordinate) = {
    val oldV =  Option(lastVelocity) getOrElse velocityInBaseFrame(old) // m/s
    val newV = velocityInBaseFrame(current) // m/s
    val sumV = oldV + newV // m/s
    val t = (oldWhen - currWhen) * 10.pow(-3) // s
    val dist = sumV*t // m
    lastVelocity = newV
    val newPos/*: breeze.linalg.Vector[Double]*/ = lastPos.vector + dist
    newPos.toCoordinate
  }
}

class ByMeanVelocityNavdataDemoPoseEstimationFeed extends PoseEstimationFeed{ def name = "ByMeanVelocityNavdataDemoPoseEstimation" }
object ByMeanVelocityNavdataDemoPoseEstimationFeed extends ByMeanVelocityNavdataDemoPoseEstimationFeed{
  def tag = ru.typeTag[ByMeanVelocityNavdataDemoPoseEstimationFeed]
}

object ByMeanVelocityNavdataDemoPoseEstimator{
  class Impl(val notifyFeed: ByMeanVelocityNavdataDemoPoseEstimationFeed,
             navdataFeed: NavdataDemoFeed,
             envZero: Environment#Coordinate,
             forwarderRef: ForwarderLazyRef) extends ByNavdataDemoDiffPoseEstimator(navdataFeed, envZero)
    with ByMeanVelocityNavdataDemoPoseEstimator
    with PoseNotifier[NavdataDemo]
  {
    type NotifyFeed = ByMeanVelocityNavdataDemoPoseEstimationFeed
    def forwarder = forwarderRef.get

    def buildData = {
      case (NavdataDemoFeed, Some(data: NavdataDemoFeed#Data))  => data
      case (NavdataDemoFeed, data: NavdataDemoFeed#Data)        => data
    }

    var on_? = false
    def start() = {
      on_? = true
      forwarder ! Subscribe(navdataFeed)
    }
    def stop() = { on_? = false }
  }

  def props(navdataFeed: NavdataDemoFeed, notifyFeed: ByMeanVelocityNavdataDemoPoseEstimationFeed,
            envZero: Environment#Coordinate, forwarder: ForwarderLazyRef, startup: Timeout, stop: Timeout) =
    FeedNotifierProps(classOf[Impl], notifyFeed, navdataFeed, envZero, forwarder)("ByMeanVelocityNavdataDemoPoseEstimator",
      startup, stop)

}