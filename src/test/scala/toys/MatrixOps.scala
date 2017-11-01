package dsptools.toys

import chisel3._
import chisel3.util.ShiftRegister
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

import scala.collection.immutable.ListMap

// TODO: Ops need to work on 1x1 too!
// TODO: Make # of pipeline stages not fixed! (Search ShiftRegister)
// WARNING: Breeze changes along columns first
object Matrix {
  def tpe[T <: Data:RealBits](el: CustomBundle[CustomBundle[T]], depth: Int): Matrix[T] =
    new Matrix(el.cloneType, depth)
  def wire[T <: Data:RealBits](el: CustomBundle[CustomBundle[T]]): Matrix[T] = {
    val result = Wire(Matrix.tpe(el, depth = 0))
    result.elB := el
    result
  }
  def apply[T <: Data:RealBits](el: CustomBundle[T]): Matrix[T] = {
    val len = el.seq.length
    require((len & (len - 1)) == 0, "Length must be power of 2!")
    val n = math.sqrt(len).toInt
    val el2d = el.seq.grouped(n).toSeq
    val el2dBundle = CustomBundle(el2d.map { case row => CustomBundle(row) })
    val result = Wire(Matrix.tpe(el2dBundle, depth = 0))
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
    Matrix(lits.grouped(n).toSeq, depth = 0)
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
  def apply[T <: Data:RealBits](s: Seq[Seq[T]], depth: Int): Matrix[T] = {
    val bundle = CustomBundle(s.map { case row => CustomBundle(row) })
    val result = Wire(Matrix.tpe(bundle, depth))
    toSeq2D(result.elB).flatten.zip(s.flatten) foreach { case (lhs, rhs) => lhs := rhs }
    result
  }
}

/** Recursive operations https://arxiv.org/pdf/1410.1599.pdf */
class Matrix[T <: Data:RealBits](val elB: CustomBundle[CustomBundle[T]], val depth: Int) extends Record {

  val elements: ListMap[String, CustomBundle[CustomBundle[T]]] = ListMap("elB" -> elB)

  val el = Matrix.toSeq2D(elements("elB"))
  el foreach { row => require(el.length == row.length, "Matrix must be square!") }
  val n = el.length
  require((n & (n - 1)) == 0, "n must be power of 2!")
  override def cloneType: this.type = {
    Matrix.tpe(elB, depth).asInstanceOf[this.type]
  }
  def isUnit = el.flatten.length == 1
  def split: Seq[Matrix[T]] = {
    if (el.flatten.length == 1) {
      Seq(this)
    }
    else {
      val el11 = Matrix(el.dropRight(n / 2).map(_.dropRight(n / 2)), depth)
      el11.suggestName(s"split11_depth${el11.depth}")
      val el12 = Matrix(el.dropRight(n / 2).map(_.drop(n / 2)), depth)
      el12.suggestName(s"split12_depth${el12.depth}")
      val el21 = Matrix(el.drop(n / 2).map(_.dropRight(n / 2)), depth)
      el21.suggestName(s"split21_depth${el21.depth}")
      val el22 = Matrix(el.drop(n / 2).map(_.drop(n / 2)), depth)
      el22.suggestName(s"split22_depth${el22.depth}")
      Seq(el11, el12, el21, el22)
    }
  }

  def putTogether(xx11: Matrix[T], xx12: Matrix[T], xx21: Matrix[T], xx22: Matrix[T], op: String): Matrix[T] = {
    val x11 = Matrix.toSeq2D(xx11.elB)
    val x12 = Matrix.toSeq2D(xx12.elB)
    val x21 = Matrix.toSeq2D(xx21.elB)
    val x22 = Matrix.toSeq2D(xx22.elB)
    val top = x11.zip(x12) map { case (left, right) => left ++ right }
    val bottom = x21.zip(x22) map { case (left, right) => left ++ right }
    val out = top ++ bottom
    val result = Matrix(out, depth + 1)
    result.suggestName(s"${op}_depth${result.depth}")
    result
  }
  def + (b: Matrix[T]): Matrix[T] = {
    val as = this.split
    val bs = b.split
    val aIs2x2 = as.map(_.isUnit).reduce(_ & _)
    val bIs2x2 = bs.map(_.isUnit).reduce(_ & _)
    if (aIs2x2 && bIs2x2) {
      val a2x2 = as.map(_.el.flatten.head)
      val b2x2 = bs.map(_.el.flatten.head)
      // Base type
      val Seq(c11, c12, c21, c22) = a2x2.zip(b2x2).map { case (x, y) => x context_+ y }
      val out = Seq(Seq(c11, c12), Seq(c21, c22))
      val result = Matrix(out, depth + 1)
      result.suggestName(s"add_depth${result.depth}")
      result
    }
    else {
      val Seq(a11, a12, a21, a22) = as
      val Seq(b11, b12, b21, b22) = bs
      putTogether(a11 + b11, a12 + b12, a21 + b21, a22 + b22, "add")
    }
  }
  def - (b: Matrix[T]): Matrix[T] = {
    val as = this.split
    val bs = b.split
    val aIs2x2 = as.map(_.isUnit).reduce(_ & _)
    val bIs2x2 = bs.map(_.isUnit).reduce(_ & _)
    if (aIs2x2 && bIs2x2) {
      val a2x2 = as.map(_.el.flatten.head)
      val b2x2 = bs.map(_.el.flatten.head)
      // Base type
      val Seq(c11, c12, c21, c22) = a2x2.zip(b2x2).map { case (x, y) => x context_- y }
      val out = Seq(Seq(c11, c12), Seq(c21, c22))
      val result = Matrix(out, depth + 1)
      result.suggestName(s"sub_depth${result.depth}")
      result
    }
    else {
      val Seq(a11, a12, a21, a22) = as
      val Seq(b11, b12, b21, b22) = bs
      putTogether(a11 - b11, a12 - b12, a21 - b21, a22 - b22, "sub")
    }
  }
  def * (b: Matrix[T]): Matrix[T] = {
    val as = this.split
    val bs = b.split
    val aIs2x2 = as.map(_.isUnit).reduce(_ & _)
    val bIs2x2 = bs.map(_.isUnit).reduce(_ & _)
    if (aIs2x2 && bIs2x2) {
      val Seq(a11, a12, a21, a22) = as.map(_.el.flatten.head)
      val Seq(b11, b12, b21, b22) = bs.map(_.el.flatten.head)
    /*
      // Strassen Winograd
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
    */
      // Strassen
      val m1a = a11 context_+ a22
      val m1b = b11 context_+ b22
      val m2a = a21 context_+ a22
      val m2b = b11
      val m3a = a11
      val m3b = b12 context_- b22
      val m4a = a22
      val m4b = b21 context_- b11
      val m5a = a11 context_+ a12
      val m5b = b22
      val m6a = a21 context_- a11
      val m6b = b11 context_+ b12
      val m7a = a12 context_- a22
      val m7b = b21 context_+ b22
      val Seq(m1, m2, m3, m4, m5, m6, m7) = DspContext.withNumMulPipes(1) {
        val m1 = m1a context_* m1b
        val m2 = m2a context_* m2b
        val m3 = m3a context_* m3b
        val m4 = m4a context_* m4b
        val m5 = m5a context_* m5b
        val m6 = m6a context_* m6b
        val m7 = m7a context_* m7b
        Seq(m1, m2, m3, m4, m5, m6, m7)
      }
      val c11a = m1 context_+ m4
      val c11b = m7 context_- m5
      val c22a = m1 context_- m2
      val c22b = m3 context_+ m6
      val c12 = m3 context_+ m5
      val c21 = m2 context_+ m4
      val c11 = c11a context_+ c11b
      val c22 = c22a context_+ c22b
      val out = Seq(Seq(c11, c12), Seq(c21, c22))
      val result = ShiftRegister(Matrix(out, depth + 1), 1)
      result.suggestName(s"mul_depth${result.depth}")
      result
    }
    else {
      val Seq(a11, a12, a21, a22) = as
      val Seq(b11, b12, b21, b22) = bs
    /*
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
    */
      val m1a = a11 + a22
      val m1b = b11 + b22
      val m2a = a21 + a22
      val m2b = b11
      val m3a = a11
      val m3b = b12 - b22
      val m4a = a22
      val m4b = b21 - b11
      val m5a = a11 + a12
      val m5b = b22
      val m6a = a21 - a11
      val m6b = b11 + b12
      val m7a = a12 - a22
      val m7b = b21 + b22
      val m1 = ShiftRegister(m1a * m1b, 1)
      val m2 = ShiftRegister(m2a * m2b, 1)
      val m3 = ShiftRegister(m3a * m3b, 1)
      val m4 = ShiftRegister(m4a * m4b, 1)
      val m5 = ShiftRegister(m5a * m5b, 1)
      val m6 = ShiftRegister(m6a * m6b, 1)
      val m7 = ShiftRegister(m7a * m7b, 1)
      val c11a = m1 + m4
      val c11b = m7 - m5
      val c22a = m1 - m2
      val c22b = m3 + m6
      val c12 = m3 + m5
      val c21 = m2 + m4
      val c11 = c11a + c11b
      val c22 = c22a + c22b
      ShiftRegister(putTogether(c11, c12, c21, c22, "mul"), 1)
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

  def rotateLeft[A](seq: Seq[A], i: Int): Seq[A] = {
    val size = seq.size
    val (first, last) = seq.splitAt(i % size)
    last ++ first
  }

  val tDut = testMod.dut
  val n = tDut.n
  val isLit = tDut.op.startsWith("lit")
  val in = tDut.litSeq
  val tvs = in.indices.map(x => rotateLeft(in, x))
  val litMatrix = DenseVector(tvs(0).toArray).toDenseMatrix.reshape(n, n).t

  var pipelineDepth = 0

  for (i <- 0 until 2 * in.length) {
    tvs(i % tvs.length).zipWithIndex foreach { case (value, idx) =>
      poke(testMod.getIO("a").asInstanceOf[CustomBundle[T]](idx), value)
      if (!isLit) poke(testMod.getIO("b").asInstanceOf[CustomBundle[T]](idx), value)
    }
    if (pipelineDepth == 0) {
      val breezeMatrix = litMatrix
      val expected = tDut.op match {
        case "add" => breezeMatrix + breezeMatrix
        case "sub" => breezeMatrix - breezeMatrix
        case "mul" => breezeMatrix * breezeMatrix
        case "litAdd" => breezeMatrix + litMatrix
        case "litSub" => breezeMatrix - litMatrix
        case "litMul" => breezeMatrix * litMatrix
      }
      val firstCorrect = expected.t.toDenseVector.toArray.toSeq.zipWithIndex.map { case (value, idx) =>
        peek(testMod.getIO("out").asInstanceOf[CustomBundle[T]](idx)) == value
      }.reduce(_ & _)
      if (firstCorrect) pipelineDepth = i
    }
    else {
      val breezeMatrix = DenseVector(tvs((i - pipelineDepth) % tvs.length).toArray).toDenseMatrix.reshape(n, n).t
      val expected = tDut.op match {
        case "add" => breezeMatrix + breezeMatrix
        case "sub" => breezeMatrix - breezeMatrix
        case "mul" => breezeMatrix * breezeMatrix
        case "litAdd" => breezeMatrix + litMatrix
        case "litSub" => breezeMatrix - litMatrix
        case "litMul" => breezeMatrix * litMatrix
      }
      expected.t.toDenseVector.toArray.toSeq.zipWithIndex foreach { case (value, idx) =>
        expect(testMod.getIO("out").asInstanceOf[CustomBundle[T]](idx), value)
      }
    }
    step(1)
  }
  println(s"Pipeline depth: $pipelineDepth")
  require(pipelineDepth > 0, "Pipeline depth shouldn't be 0!")
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
    dsptools.Driver.execute(() => new TestModule(() => new MatrixOp(inI, outI, n, "mul", in)), IATest.options("MatrixMul-I", trace = false)) {
      c => new MatrixOpTester(c)
    } should be (true)
  }

  it should "properly multiply - FixedPoint" in {
    dsptools.Driver.execute(() => new TestModule(() => new MatrixOp(inF, outF, n, "mul", in)), IATest.options("MatrixMul-F")) {
      c => new MatrixOpTester(c)
    } should be (true)
  }

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
    dsptools.Driver.execute(() => new TestModule(() => new MatrixOp(inI, outI, n, "litMul", in)), IATest.options("MatrixLitMul-I", trace = false)) {
      c => new MatrixOpTester(c)
    } should be (true)
  }

  it should "properly multiply with lit - FixedPoint" in {
    dsptools.Driver.execute(() => new TestModule(() => new MatrixOp(inF, outF, n, "litMul", in)), IATest.options("MatrixLitMul-F")) {
      c => new MatrixOpTester(c)
    } should be (true)
  }

  it should "properly add - DspReal" in {
    dsptools.Driver.execute(() => new TestModule(() => new MatrixOp(real, real, n, "add", in)), IATest.options("MatrixAdd-R", verbose = false)) {
      c => new MatrixOpTester(c)
    } should be (true)
  }

  it should "properly add with lit - DspReal" in {
    dsptools.Driver.execute(() => new TestModule(() => new MatrixOp(real, real, n, "litAdd", in)), IATest.options("MatrixLitAdd-R", verbose = false)) {
      c => new MatrixOpTester(c)
    } should be (true)
  }
}

class MatMulSpec extends FlatSpec with Matchers {

  val n = 8

  val len = n * n
  val in = (0 until len).map(_.toDouble)

  val inI = Interval(range"[0, ${len}).0")
  val outI = Interval(range"[?, ?].0")
  val inF = FixedPoint((BigInt(len - 1).bitLength + 1).W, 0.BP)
  val outF = FixedPoint(UnknownWidth(), 0.BP)
  val real = DspReal()

  behavior of "Matrix operations"

  it should "properly multiply - Interval" in {
    dsptools.Driver.execute(() => new TestModule(() => new MatrixOp(inI, outI, n, "mul", in)), IATest.options(s"MatrixMul-I-${n}x${n}", backend = "verilator")) {
      c => new MatrixOpTester(c)
    } should be (true)
  }

  it should "properly multiply - FixedPoint" in {
    dsptools.Driver.execute(() => new TestModule(() => new MatrixOp(inF, outF, n, "mul", in)), IATest.options(s"MatrixMul-F-${n}x${n}", backend = "verilator")) {
      c => new MatrixOpTester(c)
    } should be (true)
  }

}