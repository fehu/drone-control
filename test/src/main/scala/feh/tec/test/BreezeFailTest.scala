package feh.tec.test

import breeze.linalg.DenseVector
import feh.tec.test.ControlState.Default
import Math._

object BreezeFailTest extends App{
  implicit def coordinateToVectorWrapper(c: (Double, Double, Double)) = DenseVector(c._1, c._2, c._3)
  implicit class CoordinateWrapper(c: (Double, Double, Double)) {
    def vector = coordinateToVectorWrapper(c)
  }
  implicit class BreezeVectorToCoordinate(v: breeze.linalg.Vector[Double]){
    def toCoordinate = {
      assert(v.size == 3, s"only vectors from R3 can be converted to coordinate, $v")
      (v(0), v(1), v(2))
    }
  }


  def navdataRTY(nav: NavdataDemo) = rpy(nav.roll, nav.pitch, nav.yaw)
  def velocityVector(nav: NavdataDemo) = DenseVector(nav.vx.toDouble, nav.vy.toDouble, nav.vz.toDouble)//.t
  // todo: in need of jacobian?
  def velocityInBaseFrame(nav: NavdataDemo) = navdataRTY(nav) * velocityVector(nav)

  var lastVelocity: breeze.linalg.Vector[Double] = _

  def position(current: NavdataDemo, currWhen: Long, old: NavdataDemo, oldWhen: Long, lastPos: (Double, Double, Double)) = {
    val oldV =  Option(lastVelocity) getOrElse velocityInBaseFrame(old) // m/s
    val newV = velocityInBaseFrame(current) // m/s
    val sumV = oldV + newV // m/s
    val t = (oldWhen - currWhen) * 10.pow(-3) // s
    val dist = sumV*t // m
    lastVelocity = newV
    val newPos/*: breeze.linalg.Vector[Double]*/ = lastPos.vector + dist
    newPos.toCoordinate
  }

  val nav1 = NavdataDemo(Default, 0, 0,0,0, 0, 0,0,0, 0,0,0)
  val nav2 = NavdataDemo(Default, 0, 0,0,0, 0, 0,0,0, 0,0,0)
  val t1 = 0
  val t2 = 10
  val lastPos = (0d,0d,0d)

  def test = position(nav1, t1, nav2, t2, lastPos)

  println(test)
}