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

object Coordinate{
  implicit class CoordinateOps(coord: Environment#Coordinate){
    private type Coord = Environment#Coordinate

    def x = coord._1
    def y = coord._2
    def z = coord._3

    def +(other: Coord): Coord = (coord._1 + other._1, coord._2 + other._2, coord._3 + other._3)
    def -(other: Coord): Coord = (coord._1 - other._1, coord._2 - other._2, coord._3 - other._3)
    def map(f: Double => Double): Coord = (f(coord._1), f(coord._2), f(coord._3))

    def seq = Seq(x, y, z)
  }
}