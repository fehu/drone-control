package feh.tec.drone.control

import akka.actor.ActorRef
import feh.tec.drone.control.util.Math._
import breeze.linalg.DenseVector
import Environment._

trait PositionEstimator[Nav <: NavigationData] extends DataListener[Nav]{
  def estimatePosition(data: Nav): Environment#Coordinate
}

case class PositionEstimated(position: Environment#Coordinate)

trait NavdataDemoPositionEstimator extends PositionEstimator[NavdataDemo]{
  def lastData: NavdataDemo
  def lastReceived: Long
  def lastPosition: Environment#Coordinate
}

trait PositionNotifier[Nav <: NavigationData] extends PositionEstimator[Nav]{
  def listener: ActorRef

  def on_? : Boolean

  def notifyPosition(pos: Environment#Coordinate) = if(on_?) listener ! PositionEstimated(pos)

  def forwarded(data: Nav) = notifyPosition(estimatePosition(data))
}

/** Estimates position change using current navdata, the previous one and time between reception.
  */
abstract class ByNavdataDemoDiffPositionEstimator(feed: NavdataDemoFeed, envZero: Environment#Coordinate)
  extends NavdataDemoPositionEstimator
{

  val feeds = Map(feedsEntry[NavdataDemoFeed](feed, identity))

  var lastData: NavdataDemo = _
  var lastReceived: Long = _
  var lastPosition: Environment#Coordinate = _
  var isFirst = true

  def estimatePosition(data: NavdataDemo) = {
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
    lastPosition
  }

  def position(current: NavdataDemo, currWhen: Long, old: NavdataDemo, oldWhen: Long, lastPos: Environment#Coordinate): Environment#Coordinate

}

/** Estimates movement using sum of previous and current velocity vectors.
  *  Uses messages delay as time to calculate distance travelled.
  */
trait ByMeanVelocityNavdataDemoPositionEstimator extends ByNavdataDemoDiffPositionEstimator
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