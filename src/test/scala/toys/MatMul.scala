package dsptools.toys

import chisel3._
import dsptools.numbers._
import breeze.linalg.{DenseVector, DenseMatrix}
import generatortools.io.CustomBundle
import dsptools.DspContext

import chisel3.experimental._
import generatortools.testing.TestModule
import chisel3.internal.firrtl.{IntervalRange, KnownBinaryPoint, UnknownWidth}
import dsptools.DspTester
import dsptools.intervals.tests._

import org.scalatest.{Matchers, FlatSpec}

// TODO: Remove extra wires
object Matrix {
  def tpe[T <: Data:RealBits](el: CustomBundle[CustomBundle[T]]): Matrix[T] = new Matrix(el.cloneType)
  def wire[T <: Data:RealBits](el: CustomBundle[CustomBundle[T]]): Matrix[T] = {
    val result = Wire(Matrix.tpe(el))
    result.elB := el
    result
  }
  def apply[T <: Data:RealBits](el: CustomBundle[T]): Matrix[T] = {
    val len = el.seq.length
    require((len & (len - 1)) == 0, "Length must be power of 2!")
    val n = math.sqrt(len).toInt
    val el2d = el.seq.grouped(n).toSeq
    val el2dBundle = CustomBundle(el2d.map { case row => CustomBundle(row) })
    val result = Wire(Matrix.tpe(el2dBundle))
    result.elB.seq.map(_.seq).flatten.zip(el.seq) foreach { case (lhs, rhs) => lhs := rhs}
    result
  }
  def toCustomBundle[T <: Data:RealBits](m: Matrix[T]): CustomBundle[T] = {
    val els = m.el.seq.map(_.seq).flatten
    val result = Wire(CustomBundle(els))
    result.seq.zip(els) foreach { case (lhs, rhs) => lhs := rhs}
    result
  }
  def vectorLit[T <: Data:RealBits](m: DenseVector[Double], bp: Int): CustomBundle[T] = {
    val s = m.toArray.toSeq
    val lits = s.map { case d => 
      DspContext.withBinaryPoint(bp) {
        implicitly[ConvertableTo[T]].fromDouble(d)
      }
    }
    CustomBundle.wire(lits)
  }
  def matrixLit[T <: Data:RealBits](m: DenseMatrix[Double], bp: Int): Matrix[T] = {
    matrixLit[T](m.toDenseVector, bp)
  }
  def matrixLit[T <: Data:RealBits](m: DenseVector[Double], bp: Int): Matrix[T] = {
    val s = m.toArray.toSeq
    val lits = s.map { case d => 
      DspContext.withBinaryPoint(bp) {
        implicitly[ConvertableTo[T]].fromDouble(d)
      }
    }
    val len = s.length
    require((len & (len - 1)) == 0, "Length must be power of 2!")
    val n = math.sqrt(len).toInt
    require(n * n == len, "Matrix must be square")
    Matrix(lits.grouped(n).toSeq)
  }
  def toDenseVector(s: Seq[Double]): DenseVector[Double] = DenseVector(s.toArray)
  def toDenseMatrixFrom1D(s: Seq[Double]): DenseMatrix[Double] = {
    val nDbl = math.sqrt(s.length)
    require(math.floor(nDbl) == nDbl, "Matrix must be square!")
    toDenseVector(s).toDenseMatrix.reshape(nDbl.toInt, nDbl.toInt)
  }
  def toDenseVectorFrom2D(s: Seq[Seq[Double]]): DenseVector[Double] = {
    DenseVector(s.flatten.toArray)
  }
  def toDenseMatrix(s: Seq[Seq[Double]]): DenseMatrix[Double] = {
    s foreach { row => require(s.length == row.length, "Matrix must be square!") }
    toDenseVectorFrom2D(s).toDenseMatrix.reshape(s.length, s.length)
  }  
  def toSeq2D[T <: Data:RealBits](b: CustomBundle[CustomBundle[T]]): Seq[Seq[T]] = b.seq.map(_.seq)
  def toCustomBundle2D[T <: Data:RealBits](s: Seq[Seq[T]]): CustomBundle[CustomBundle[T]] = {
    val bundle = Wire(CustomBundle(s.map { case row => CustomBundle(row) }))
    toSeq2D(bundle).flatten.zip(s.flatten) foreach { case (lhs, rhs) => lhs := rhs }
    bundle
  }
  def apply[T <: Data:RealBits](s: Seq[Seq[T]]): Matrix[T] = {
    val bundle = CustomBundle(s.map { case row => CustomBundle(row) })
    val result = Wire(Matrix.tpe(bundle))
    toSeq2D(result.elB).flatten.zip(s.flatten) foreach { case (lhs, rhs) => lhs := rhs }
    result
  }
}

/** Recursive operations https://arxiv.org/pdf/1410.1599.pdf */
class Matrix[T <: Data:RealBits](val elB: CustomBundle[CustomBundle[T]]) extends Bundle {
  def el = Matrix.toSeq2D(elB)
  el foreach { row => require(el.length == row.length, "Matrix must be square!") }
  val n = el.length
  require((n & (n - 1)) == 0, "n must be power of 2!")
  override def cloneType: this.type = {
    Matrix.tpe(elB).asInstanceOf[this.type]
  }
  def isUnit = el.flatten.length == 1
  def split(): Seq[Matrix[T]] = {
    val el11 = Matrix(el.dropRight(n / 2).map(_.dropRight(n / 2)))
    val el12 = Matrix(el.dropRight(n / 2).map(_.drop(n / 2)))
    val el21 = Matrix(el.drop(n / 2).map(_.dropRight(n / 2)))
    val el22 = Matrix(el.drop(n / 2).map(_.drop(n / 2)))
    Seq(el11, el12, el21, el22)
  }
  def putTogether(xx11: Matrix[T], xx12: Matrix[T], xx21: Matrix[T], xx22: Matrix[T]): Matrix[T] = {
    val x11 = Matrix.toSeq2D(xx11.elB)
    val x12 = Matrix.toSeq2D(xx12.elB)
    val x21 = Matrix.toSeq2D(xx21.elB)
    val x22 = Matrix.toSeq2D(xx22.elB)
    val top = x11.zip(x12) map { case (left, right) => left ++ right }
    val bottom = x21.zip(x22) map { case (left, right) => left ++ right }
    val out = top ++ bottom
    Matrix(out)
  }
  def + (b: Matrix[T]): Matrix[T] = {
    val as = this.split()
    val bs = b.split()
    val aIs2x2 = as.map(_.isUnit).reduce(_ & _)
    val bIs2x2 = bs.map(_.isUnit).reduce(_ & _)
    if (aIs2x2 && bIs2x2) {
      val a2x2 = as.map(_.el.flatten.head)
      val b2x2 = bs.map(_.el.flatten.head)
      // Base type
      val Seq(c11, c12, c21, c22) = a2x2.zip(b2x2).map { case (x, y) => x context_+ y }
      val out = Seq(Seq(c11, c12), Seq(c21, c22))
      Matrix(out)
    }
    else {
      val Seq(a11, a12, a21, a22) = as
      val Seq(b11, b12, b21, b22) = bs
      putTogether(a11 + b11, a12 + b12, a21 + b21, a22 + b22)
    }
  }
  def - (b: Matrix[T]): Matrix[T] = {
    val as = this.split()
    val bs = b.split()
    val aIs2x2 = as.map(_.isUnit).reduce(_ & _)
    val bIs2x2 = bs.map(_.isUnit).reduce(_ & _)
    if (aIs2x2 && bIs2x2) {
      val a2x2 = as.map(_.el.flatten.head)
      val b2x2 = bs.map(_.el.flatten.head)
      // Base type
      val Seq(c11, c12, c21, c22) = a2x2.zip(b2x2).map { case (x, y) => x context_- y }
      val out = Seq(Seq(c11, c12), Seq(c21, c22))
      Matrix(out)
    }
    else {
      val Seq(a11, a12, a21, a22) = as
      val Seq(b11, b12, b21, b22) = bs
      putTogether(a11 - b11, a12 - b12, a21 - b21, a22 - b22)
    }
  }
  def * (b: Matrix[T]): Matrix[T] = {
    val as = this.split()
    val bs = b.split()
    val aIs2x2 = as.map(_.isUnit).reduce(_ & _)
    val bIs2x2 = bs.map(_.isUnit).reduce(_ & _)
    if (aIs2x2 && bIs2x2) {
      val Seq(a11, a12, a21, a22) = as.map(_.el.flatten.head)
      val Seq(b11, b12, b21, b22) = bs.map(_.el.flatten.head)
      val s1 = a21 context_+ a22
      val s2 = s1 context_- a11
      val s3 = a11 context_- a21
      val s4 = a12 context_- s2
      val s5 = b12 context_- b11
      val s6 = b22 context_- s5
      val s7 = b22 context_- b12
      val s8 = s6 context_- b21
      val m1 = s2 context_* s6
      val m2 = a11 context_* b11
      val m3 = a12 context_* b21
      val m4 = s3 context_* s7
      val m5 = s1 context_* s5
      val m6 = s4 context_* b22
      val m7 = a22 context_* s8
      val t1 = m1 context_+ m2
      val t2 = t1 context_+ m4
      val c11 = m2 context_+ m3
      val c12 = t1 context_+ m5 context_+ m6
      val c21 = t2 context_- m7
      val c22 = t2 context_+ m5
      val out = Seq(Seq(c11, c12), Seq(c21, c22))
      Matrix(out)
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
}

@chiselName
class MatrixOp[T <: Data:RealBits](
    genIn : => T, 
    genOut : => T, 
    val n: Int, 
    val op: String, 
    val litSeq: Seq[Double] = Seq.empty) extends Module {
  val io = IO(new Bundle {
    val a = Input(CustomBundle(Seq.fill(n * n)(genIn)))
    val b = Input(CustomBundle(Seq.fill(if (op.startsWith("lit")) 1 else n * n)(genIn)))
    val out = Output(CustomBundle(Seq.fill(n * n)(genOut)))
  })
  val bp = genIn match {
    case i: Interval => 
      i.range.binaryPoint match {
        case KnownBinaryPoint(bp) => bp
        case _ => 0
      }
    case f: FixedPoint =>
      f.binaryPoint match {
        case KnownBinaryPoint(bp) => bp
        case _ => 0
      }
    case _ => 0
  }
  val a = Matrix(io.a)
  val b = Matrix(io.b)
  val out = if (op.startsWith("lit")) {
    require(litSeq.length == n * n, "Lit sequence length doesn't match matrix size!")
    val matrixLit = Matrix.matrixLit[T](DenseVector(litSeq.toArray), bp)
    op match {
      case "litAdd" => a + matrixLit
      case "litSub" => a - matrixLit
      case "litMul" => a * matrixLit
    }
  }
  else {
    op match {
      case "add" => a + b
      case "sub" => a - b
      case "mul" => a * b
    }
  }
  io.out.seq.zip(Matrix.toSeq2D(out.elB).flatten) foreach { case (lhs, rhs) => lhs := rhs }
}

class MatrixOpTester[T <: Data:RealBits](testMod: TestModule[MatrixOp[T]]) extends DspTester(testMod) {
  val tDut = testMod.dut
  val isLit = tDut.op.startsWith("lit")
  val in = tDut.litSeq
  val n = tDut.n
  in.zipWithIndex foreach { case (value, idx) => 
    poke(testMod.getIO("a").asInstanceOf[CustomBundle[T]](idx), value)
    if (!isLit) poke(testMod.getIO("b").asInstanceOf[CustomBundle[T]](idx), value)
  }
  val breezeMatrix = DenseVector(in.toArray).toDenseMatrix.reshape(n, n)
  val expected = tDut.op match {
    case "litAdd" | "add" => breezeMatrix + breezeMatrix
    case "litSub" | "sub" => breezeMatrix - breezeMatrix
    case "litMul" | "mul" => breezeMatrix * breezeMatrix
  }
  expected.toDenseVector.toArray.toSeq.zipWithIndex foreach { case (value, idx) =>
    expect(testMod.getIO("out").asInstanceOf[CustomBundle[T]](idx), value)
  }
}

class MatrixOpSpec extends FlatSpec with Matchers {
  val n = 8
  val len = n * n
  val in = (0 until len).map(_.toDouble)
  val inI = Interval(range"[0, ${len}).0")
  val outI = Interval(range"[?, ?].0")
  val inF = FixedPoint((BigInt(len - 1).bitLength + 1).W, 0.BP)
  val outF = FixedPoint(UnknownWidth(), 0.BP)
  val real = DspReal()

  behavior of "Matrix operations"
/*
  it should "properly add - Interval" in {
    dsptools.Driver.execute(() => new TestModule(() => new MatrixOp(inI, outI, n, "add", in)), IATest.options("MatrixAdd")) {
      c => new MatrixOpTester(c)
    } should be (true)
  }

  it should "properly subtract - Interval" in {
    dsptools.Driver.execute(() => new TestModule(() => new MatrixOp(inI, outI, n, "sub", in)), IATest.options("MatrixSub")) {
      c => new MatrixOpTester(c)
    } should be (true)
  }

  it should "properly multiply - Interval" in {
    dsptools.Driver.execute(() => new TestModule(() => new MatrixOp(inI, outI, n, "mul", in)), IATest.options("MatrixMul-I", trace = true)) {
      c => new MatrixOpTester(c)
    } should be (true)
  }
*/  

  it should "properly multiply - FixedPoint" in {
    dsptools.Driver.execute(() => new TestModule(() => new MatrixOp(inF, outF, n, "mul", in)), IATest.options("MatrixMul-F")) {
      c => new MatrixOpTester(c)
    } should be (true)
  }
/*
  it should "properly add with lit - Interval" in {
    dsptools.Driver.execute(() => new TestModule(() => new MatrixOp(inI, outI, n, "litAdd", in)), IATest.options("MatrixLitAdd")) {
      c => new MatrixOpTester(c)
    } should be (true)
  }

  it should "properly subtract with lit - Interval" in {
    dsptools.Driver.execute(() => new TestModule(() => new MatrixOp(inI, outI, n, "litSub", in)), IATest.options("MatrixLitSub")) {
      c => new MatrixOpTester(c)
    } should be (true)
  }

  it should "properly multiply with lit - Interval" in {
    dsptools.Driver.execute(() => new TestModule(() => new MatrixOp(inI, outI, n, "litMul", in)), IATest.options("MatrixLitMul-I")) {
      c => new MatrixOpTester(c)
    } should be (true)
  }

  it should "properly multiply with lit - FixedPoint" in {
    dsptools.Driver.execute(() => new TestModule(() => new MatrixOp(inF, outF, n, "litMul", in)), IATest.options("MatrixLitMul-F")) {
      c => new MatrixOpTester(c)
    } should be (true)
  }
*/    
}

// TODO: Pipeline matrix multiply, Why is DspReal just not connected at the input? (Does Complex work?)
// n =4, n = 8 have compilation problems