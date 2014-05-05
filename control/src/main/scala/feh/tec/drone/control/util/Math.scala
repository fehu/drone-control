package feh.tec.drone.control.util

import scala.math._
import scala.specialized
import breeze.linalg.{DenseVector, DenseMatrix, Matrix}
import feh.util._
import breeze.math.Semiring
import scala.reflect.ClassTag
import breeze.storage.DefaultArrayValue

object Math {
  trait Size
  trait LinSize extends Size
  
  trait _1 extends LinSize
  trait _2 extends LinSize
  trait _3 extends LinSize
  trait _4 extends LinSize
  trait _5 extends LinSize

  trait SizeEvidence
  class LinSizeEvidence[LS <: LinSize](val linSize: Int) extends SizeEvidence{
    override def toString = linSize.toString
  }

  implicit object _1 extends LinSizeEvidence[_1](1)
  implicit object _2 extends LinSizeEvidence[_2](2)
  implicit object _3 extends LinSizeEvidence[_3](3)
  implicit object _4 extends LinSizeEvidence[_4](4)
  implicit object _5 extends LinSizeEvidence[_5](5)
  
  case class MatrixSize[NRows <: LinSize, NCols <: LinSize](implicit val nRows: LinSizeEvidence[NRows],
                                                            val nCols: LinSizeEvidence[NCols]) extends Size with SizeEvidence{
    override def toString = nRows + "x" + nCols
  }
  type x[NRows <: LinSize, NCols <: LinSize] = MatrixSize[NRows, NCols]
  implicit def matrixSizeBuilder[NRows <: LinSize, NCols <: LinSize](implicit nRows: LinSizeEvidence[NRows],
                                                                              nCols: LinSizeEvidence[NCols]): MatrixSize[NRows, NCols] =
    MatrixSize[NRows, NCols]
  
  case class SizedMatrix[@specialized(Int,Long,Float,Double) V, M <: Matrix[V], MSize <: MatrixSize[_, _]](matrix: M, size: MSize){
    assert(matrix.rows == size.nRows.linSize && matrix.cols == size.nCols.linSize)

    override def toString = matrix.toString()
  }
  object SizedMatrix{
    implicit def sizedMatrixUnwrapper[T, M <: Matrix[T]](sm: SizedMatrix[T, M, _]): M = sm.matrix
  }

  object Matrix{
    trait MatrixCreator[@specialized(Int,Long,Float,Double) V]{
      def ofSize[S <: MatrixSize[_, _]]: SizedMatrixCreator[S]

      trait SizedMatrixCreator[S <: MatrixSize[_, _]]{
        def apply[M <: Matrix[V]](m: M)(implicit size: S): SizedMatrix[V, M, S]
      }
    }

    def apply[V] = new MatrixCreator[V] {
      def ofSize[Size <: MatrixSize[_, _]] = new SizedMatrixCreator[Size] {
        def apply[M <: Matrix[V]](m: M)(implicit size: Size) = new SizedMatrix[V, M, Size](m, size)
      }
    }

  }

  type RotationMatrix[T] = SizedMatrix[T, Matrix[T], _3 x _3]
  def RotationMatrix[T](m: Matrix[T]): RotationMatrix[T] = Matrix[T].ofSize[_3 x _3](m)

  implicit class RotationMatrixWrapper[T: ClassTag: DefaultArrayValue: Semiring](m: RotationMatrix[T]){
    def transformation = {
      val m2 = DenseMatrix.zeros[T](4, 4)
      m2(0 to 2, 0 to 2) := m.toDenseMatrix
      m2(3, 3) = implicitly[Semiring[T]].one
      TransformationMatrix(m2)
    }
  }

  /** Roll-Pitch-Yaw
    * @param roll roll angle in radians
    * @param pitch pitch angle in radians
    * @param yaw yaw angle in radians
    * @return 3x3 rotation matrix
    */
  def rpy(roll: Double, pitch: Double, yaw: Double): RotationMatrix[Double] = RotationMatrix(DenseMatrix(
    (cos(pitch)*cos(yaw),                             -cos(pitch)*sin(yaw),                               sin(pitch)),
    (sin(roll)*sin(pitch)*cos(yaw)+cos(roll)*sin(yaw), cos(roll)*cos(yaw)-sin(roll)*sin(pitch)*sin(yaw), -sin(roll)*cos(pitch)),
    (sin(roll)*sin(yaw)-cos(roll)*sin(pitch)*cos(yaw), sin(roll)*cos(yaw)+cos(roll)*sin(pitch)*sin(yaw),  cos(roll)*cos(pitch))
  ))
  
  type TransformationMatrix[T] = SizedMatrix[T, Matrix[T], _4 x _4]
  def TransformationMatrix[T](m: Matrix[T]): TransformationMatrix[T] = Matrix[T].ofSize[_4 x _4](m)

  implicit class PowWrapper(d: Double){
    def pow(p: Double) = math.pow(d, p)
    def ^(p: Double) = pow(p)
  }
}

@deprecated
class UnsignedInt (val i: Int) extends AnyVal{
  def asLong = i.toLong - Int.MinValue
  override def toString = asLong.toString
}

object UnsignedInt{
  def apply(i: Int): UnsignedInt = new UnsignedInt(i)
}

case class NotNegative[N](get: N)(implicit num: Numeric[N]){
  assert(num.gteq(get, num.zero), s"$get is negative")
}
object NotNegative{
  implicit def notNegativeUnwrapper[N](nn: NotNegative[N]): N =  nn.get
}