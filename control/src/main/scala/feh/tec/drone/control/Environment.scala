package feh.tec.drone.control

import breeze.linalg.DenseVector
import feh.util._

object Environment{
  implicit def coordinateToVectorWrapper(c: Environment#Coordinate) = DenseVector(c._1, c._2, c._3)
  implicit class CoordinateWrapper(c: Environment#Coordinate) {
    def vector = coordinateToVectorWrapper(c)
  }
  implicit class BreezeVectorToCoordinate(v: breeze.linalg.Vector[Double]){
    def toCoordinate = {
      assert(v.size == 3, s"only vectors from R3 can be converted to coordinate, $v")
      v(0) -> v(1) --> v(2)
    }
  }
}

trait Environment{
  type Coordinate = (Double, Double, Double)

  def zero: Coordinate
}

class SimpleEnvironment(val zero: (Double, Double, Double) = (0, 0, 0)) extends Environment{
  var dronePosition: Coordinate = zero
}

