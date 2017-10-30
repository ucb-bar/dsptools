package dsptools.toys

import chisel3._
import dsptools.numbers.implicits._
import dsptools.numbers._
import breeze.linalg._

import org.scalatest.{Matchers, FlatSpec}

object Matrix {
  def apply(el: Seq[Seq[Double]]) = new Matrix(el)
  def toBreeze(el: Seq[Seq[Double]], n: Int): DenseMatrix[Double] = {
    DenseVector(el.flatten.toArray).toDenseMatrix.reshape(n, n)
  }
  def toBreeze(m: Matrix, n: Int): DenseMatrix[Double] = {
    toBreeze(m.el, n)
  }
}

/** Recursive operations https://arxiv.org/pdf/1410.1599.pdf */
class Matrix(val el: Seq[Seq[Double]]) {
  el foreach { row => require(el.length == row.length, "Matrix must be square!") }
  val n = el.length
  require((n & (n - 1)) == 0, "n must be power of 2!")
  def isUnit = el.flatten.length == 1
  def split(): Seq[Matrix] = {
    val el11 = Matrix(el.dropRight(n / 2).map(_.dropRight(n / 2)))
    val el12 = Matrix(el.dropRight(n / 2).map(_.drop(n / 2)))
    val el21 = Matrix(el.drop(n / 2).map(_.dropRight(n / 2)))
    val el22 = Matrix(el.drop(n / 2).map(_.drop(n / 2)))
    Seq(el11, el12, el21, el22)
  }
  def * (b: Matrix): Matrix = {
    val as = this.split()
    val bs = b.split()
    val aIs2x2 = as.map(_.isUnit).reduce(_ & _)
    val bIs2x2 = bs.map(_.isUnit).reduce(_ & _)
    if (aIs2x2 && bIs2x2) {
      val Seq(a11, a12, a21, a22) = as.map(_.el.flatten.head)
      val Seq(b11, b12, b21, b22) = bs.map(_.el.flatten.head)
      val s1 = a21 + a22
      val s2 = s1 - a11
      val s3 = a11 - a21
      val s4 = a12 - s2
      val s5 = b12 - b11
      val s6 = b22 - s5
      val s7 = b22 - b12
      val s8 = s6 - b21
      val m1 = s2 * s6
      val m2 = a11 * b11
      val m3 = a12 * b21
      val m4 = s3 * s7
      val m5 = s1 * s5
      val m6 = s4 * b22
      val m7 = a22 * s8
      val t1 = m1 + m2
      val t2 = t1 + m4
      val c11 = m2 + m3
      val c12 = t1 + m5 + m6
      val c21 = t2 - m7
      val c22 = t2 + m5
      Matrix(Seq(Seq(c11, c12), Seq(c21, c22)))
    }
    else {
      val Seq(a11, a12, a21, a22) = as
      val Seq(b11, b12, b21, b22) = bs
      val s1 = a21 + a22
      val s2 = s1 - a11
      val s3 = a11 - a21
      val s4 = a12 - s2
      val s5 = b12 - b11
      val s6 = b22 - s5
      val s7 = b22 - b12
      val s8 = s6 - b21
      val m1 = s2 * s6
      val m2 = a11 * b11
      val m3 = a12 * b21
      val m4 = s3 * s7
      val m5 = s1 * s5
      val m6 = s4 * b22
      val m7 = a22 * s8
      val t1 = m1 + m2
      val t2 = t1 + m4
      val c11 = m2 + m3
      val c12 = t1 + m5 + m6
      val c21 = t2 - m7
      val c22 = t2 + m5
      putTogether(c11, c12, c21, c22)
    }
  }
  def + (b: Matrix): Matrix = {
    val as = this.split()
    val bs = b.split()
    val aIs2x2 = as.map(_.isUnit).reduce(_ & _)
    val bIs2x2 = bs.map(_.isUnit).reduce(_ & _)
    if (aIs2x2 && bIs2x2) {
      val a2x2 = as.map(_.el.flatten.head)
      val b2x2 = bs.map(_.el.flatten.head)
      val Seq(c11, c12, c21, c22) = a2x2.zip(b2x2).map { case (x, y) => x + y }
      Matrix(Seq(Seq(c11, c12), Seq(c21, c22)))
    }
    else {
      val Seq(a11, a12, a21, a22) = this.split()
      val Seq(b11, b12, b21, b22) = b.split()
      putTogether(a11 + b11, a12 + b12, a21 + b21, a22 + b22)
    }
  }
  def - (b: Matrix): Matrix = {
    val as = this.split()
    val bs = b.split()
    val aIs2x2 = as.map(_.isUnit).reduce(_ & _)
    val bIs2x2 = bs.map(_.isUnit).reduce(_ & _)
    if (aIs2x2 && bIs2x2) {
      val a2x2 = as.map(_.el.flatten.head)
      val b2x2 = bs.map(_.el.flatten.head)
      val Seq(c11, c12, c21, c22) = a2x2.zip(b2x2).map { case (x, y) => x - y }
      Matrix(Seq(Seq(c11, c12), Seq(c21, c22)))
    }
    else {
      val Seq(a11, a12, a21, a22) = this.split()
      val Seq(b11, b12, b21, b22) = b.split()
      putTogether(a11 - b11, a12 - b12, a21 - b21, a22 - b22)
    }
  }
  def putTogether(x11: Matrix, x12: Matrix, x21: Matrix, x22: Matrix): Matrix = {
    val top = x11.el.zip(x12.el) map { case (left, right) => left ++ right }
    val bottom = x21.el.zip(x22.el) map { case (left, right) => left ++ right }
    Matrix(top ++ bottom)
  }
}

class MatrixSpec extends FlatSpec with Matchers {

  behavior of "Matrix operations"

  it should "do the right thing :o" in {
    val n = 8
    val v = (0 until n * n).map(_.toDouble)
    val el = v.grouped(n).toSeq
    val matrix = Matrix(el)
    val sum = matrix + matrix
    val diff = matrix - matrix
    val prod = matrix * matrix

    val breezeMatrix = DenseVector(v.toArray).toDenseMatrix.reshape(n, n)
    val breezeSum = breezeMatrix + breezeMatrix
    val breezeDiff = breezeMatrix - breezeMatrix
    val breezeProd = breezeMatrix * breezeMatrix

    require(Matrix.toBreeze(sum, n) == breezeSum)
    require(Matrix.toBreeze(diff, n) == breezeDiff)
    require(Matrix.toBreeze(prod, n) == breezeProd)
  }
}



/*
object Matrix2x2 {
  def apply[T <: Data](x11: T, x12: T, x21: T, x22: T): Matrix2x2[T] = new Matrix2x2(x11, x12, x21, x22)
  def wire[T <: Data](x11: T, x12: T, x21: T, x22: T): Matrix2x2[T] = {
    val result = Wire(Matrix2x2(x11.cloneType, x12.cloneType, x21.cloneType, x22.cloneType))
    result.x11 := x11
    result.x12 := x12
    result.x21 := x21
    result.x22 := x22
    result
  }
}


*/
