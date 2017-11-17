package dsptools.toys

import breeze.signal
import breeze.signal.OptOverhang
import chisel3._
import chisel3.util.ShiftRegister
import dsptools.intervals.tests.IATest
import dsptools.numbers._
import breeze.linalg.{DenseMatrix, DenseVector}
import generatortools.io.CustomBundle
import dsptools.{DspContext, DspTester, NoTrim}
import chisel3.experimental._
import generatortools.testing.TestModule
import chisel3.internal.firrtl.{IntervalRange, KnownBinaryPoint, KnownWidth, UnknownWidth}
import firrtl.transforms.DedupModules
import org.scalatest.{FlatSpec, Matchers}

import scala.util.Random

trait NoDedupAnnotator {
  self: Module =>

  def doNotDedup(module: Module): Unit = {
    annotate(ChiselAnnotation(module, classOf[DedupModules], "nodedup!"))
  }
}

// TODO: Doesn't need to be power of 2
// TODO: Get rid of lots of 0 until n's -- switch to for/yield?
// TODO: Expand to support complex?
// TODO: DspReal tests
@chiselName
class SystolicMatMul[T <: Data:RealBits](
    genIn : => T,
    genOut : => T,
    val n: Int,
    val litSeq: Seq[Double] = Seq.empty,
    litBP: Option[Int] = None) extends Module with NoDedupAnnotator {
  val io = IO(new Bundle {
    val a = Input(CustomBundle(Seq.fill(n * n)(genIn)))
    val b = Input(CustomBundle(Seq.fill(if (litSeq.nonEmpty) 1 else n * n)(genIn)))
    val start = Input(Bool())
    val out = Output(CustomBundle(Seq.fill(n * n)(genOut)))
    val done = Output(Bool())
  })
  // TODO: Cleanly fix a -> a, b -> b
  // TODO: Support constants on both left + right
  val b = Matrix(io.a)
  val a =
    if (litSeq.nonEmpty) {
      require(litSeq.length == n * n, "Lit sequence length doesn't match matrix size!")
      Matrix.matrixLit[T](DenseVector(litSeq.toArray), litBP.get)
    }
    else Matrix(io.b)

  val delayedStarts = (1 until n).scanLeft(io.start) { case (regIn, outputIdx) =>
    val out = RegNext(next = regIn, init = false.B)
    out.suggestName(s"delayedStart_${outputIdx}")
    out
  }

  // Inputs fed in via series of counters for control logic
  val ctrlCounts = (0 until n).map { case idx =>
    val count = RegInit(n.I)
    val nextCount = Mux(count === n.I, n.I, count + 1.I)
    val nextCountWithStart = Mux(delayedStarts(idx), 0.I, nextCount)
    count := nextCountWithStart
    count.suggestName(s"ctrlCount_${idx}")
    count
  }

  // Multiplication done on the next cycle after the last inputs are fed in
  io.done := ShiftRegister(RegNext(next = ctrlCounts.last === (n - 1).I, init = false.B), n - 1)

  // TODO: Modify Chisel Mux1H to support intervals -- otherwise this is a priority mux
  def mux1h[R <: Data](choices: Seq[R], conds: Seq[Bool], otherwise: R): R = {
    val zipped = choices.zip(conds)
    zipped.foldLeft(otherwise) { case (left, (choice, cond)) =>
      Mux(cond, choice, left)
    }
  }

  // Outer = associated counter; inner = whether counter is a particular value
  val ctrlCountsAsConds = ctrlCounts.zipWithIndex.map { case (count, countIdx) =>
    (0 until n).map { case idx =>
      val out = count === idx.I
      out.suggestName(s"count${countIdx}Is_${idx}")
      out
    }
  }

  // Top entries (B) going into the systolic array
  // For each column, select which row of b should be fed in at a given time
  val topEntries = (0 until n).map { case colIdx =>
    val rows = (0 until n).map(b.el(_)(colIdx))
    val out = mux1h(rows, ctrlCountsAsConds(colIdx), Ring[T].zero)
    out.suggestName(s"topEntries_${colIdx}")
    out
  }

  // Left entries (A) going into the systolic array
  val leftEntries = (0 until n).map { case rowIdx =>
    val cols = (0 until n).map(a.el(rowIdx)(_))
    val out = mux1h(cols, ctrlCountsAsConds(rowIdx), Ring[T].zero)
    out.suggestName(s"leftEntires_${rowIdx}")
  }

  // outer = row
  val litSeq2D = litSeq.grouped(n).toSeq

  // http://www.ee.usyd.edu.au/people/philip.leong/UserFiles/File/teaching/rc-hit15/8-3-systolic.pdf
  val pes = (0 until n).map { case rowIdx =>
    (0 until n).map { case colIdx =>
      val pe = Module(
        new MatMulPE(
          leftEntries(rowIdx),
          topEntries(colIdx),
          genOut,
          n,
          if (litSeq.nonEmpty) Some(litSeq2D(rowIdx)) else None,
          litBP
        )
      )
      doNotDedup(pe)
      pe.suggestName(s"pe_${rowIdx}_${colIdx}")
      pe.io.reset := io.start
      io.out(rowIdx * n + colIdx) := pe.io.c
      pe
    }
  }

  for (rowIdx <- 0 until n; colIdx <- 0 until n) {
    val bin = if (rowIdx == 0) topEntries(colIdx) else pes(rowIdx - 1)(colIdx).io.bout
    pes(rowIdx)(colIdx).io.bin := bin
    val ain = if (colIdx == 0) leftEntries(rowIdx) else pes(rowIdx)(colIdx - 1).io.aout
    pes(rowIdx)(colIdx).io.ain := ain
  }
}

@chiselName
class MatMulPE[T <: Data:RealBits](
    genA: => T,
    genB: => T,
    genOut: => T,
    n: Int,
    litSeq: Option[Seq[Double]],
    litBP: Option[Int]) extends Module {
  val io = IO(new Bundle {
    val reset = Input(Bool())
    val ain = Input(genA)
    val bin = Input(genB)
    val aout = Output(genA)
    val bout = Output(genB)
    val c = Output(genOut)
  })
  withReset(io.reset) {
    io.aout := RegNext(next = io.ain, init = Ring[T].zero)
    io.bout := RegNext(next = io.bin, init = Ring[T].zero)
    val mul = io.ain context_* io.bin
    // Should not generate logic that gets synthesized. Simply unrolls the loop for range inference.
    val accumRange = genA match {
      case i: Interval =>
        // If b consists of lits, you know a little more about your circuit
        // Note: Always calculate ranges with everything fully factored out
        val accumRange =
          if (litSeq.nonEmpty) {
            litBP match {
              case Some(bp) =>
                val lits = litSeq.get.map(d => DspContext.withBinaryPoint(bp) {
                  ConvertableTo[T].fromDouble(d)
                })
                lits.map(a => a context_* io.bin).reduce(_ + _)
              case _ =>
                throw new Exception("Binary point must be known!")
            }
          }
          else
            Seq.fill(n)(mul).reduce(_ + _)
        Some(accumRange)
      case _ =>
        None
    }

    // How nested is an optimal adder of a sequence of #'s?
    val nestDepth = math.ceil(math.log(n) / math.log(2)).toInt

    val accum = mul match {
      case f: FixedPoint =>
        (f.widthOption, f.binaryPoint) match {
          case (Some(w), KnownBinaryPoint(bp)) =>
            // When adding: width = max(a_intwidth, b_intwidth) + 1 + max(a_bp, b_bp)
            // bp = max(a_bp, b_bp)
            val reg = RegInit(t = FixedPoint((w + nestDepth).W, bp.BP), init = 0.0.F(bp.BP))
            reg := reg +& f
            reg
        }
      case r: DspReal =>
        val reg = RegInit(DspReal(0.0))
        reg := reg + r
        reg
      case s: SInt =>
        // When adding: width = max(a_width, b_width) + 1
        s.widthOption match {
          case Some(w) =>
            val reg = RegInit(t = SInt((w + nestDepth).W), init = 0.S)
            reg := reg +& s
            reg
        }
      case u: UInt =>
        u.widthOption match {
          case Some(w) =>
            val reg = RegInit(t = UInt((w + nestDepth).W), init = 0.U)
            reg := reg +& u
            reg
        }
      case i: Interval =>
        val reg = RegInit(Interval(), 0.0.I(0.BP))
        reg := (reg + i).reassignInterval(accumRange.get.asInstanceOf[Interval])
        reg
    }
    accum.suggestName("accum")
    io.c := accum.asInstanceOf[T]
  }
}

object MatMulTests {
  def rotateLeft[A](seq: Seq[A], i: Int): Seq[A] = {
    val size = seq.size
    val (first, last) = seq.splitAt(i % size)
    last ++ first
  }
  def generateSimpleInputs(n: Int) = {
    val simple = (0 until n * n).map(_.toDouble)
    val out = simple.indices.map(x => rotateLeft(simple, x))
    val largest = Seq.fill(n * n)((n * n - 1).toDouble)
    val smallest = Seq.fill(n * n)(-(n * n).toDouble)
    Seq(largest, smallest) ++ out
  }
  // TODO: Redundant with function in object Matrix
  def convertToDenseMatrix(in: Seq[Double]) = {
    val n = math.sqrt(in.length).toInt
    toDenseVector(in).toDenseMatrix.reshape(n, n).t
  }
  def getElement[T <: Data](in: Data, idx: Int) = in.asInstanceOf[CustomBundle[T]](idx)
  def convertToSeq(in: DenseMatrix[Double]) = {
    in.t.toDenseVector.toArray.toSeq
  }
  def dct(n: Int): Seq[Double] = {
    // https://www.mathworks.com/help/images/discrete-cosine-transform.html
    for (p <- 0 until n; q <- 0 until n) yield {
      if (p == 0) 1.toDouble / math.sqrt(n)
      else math.sqrt(2.toDouble / n) * math.cos(math.Pi * p * (2 * q + 1) / 2 / n)
    }
  }
  def generateRandomInputs(n: Int, numTests: Int = 100, maxNotInclusive: Int = 2): Seq[Seq[Double]] = {
    val lowerBound = -maxNotInclusive
    val upperBoundRand = 2 * maxNotInclusive - 1
    Seq.fill(n * n * numTests)(lowerBound + Random.nextInt(upperBoundRand).toDouble).grouped(n * n).toSeq
  }
  def toDenseVector(in: Seq[Double]) = DenseVector(in.toArray)
  def toSeq(in: DenseVector[Double]) = in.toArray.toSeq

  // TODO: Check
  def filter(inTemp: Seq[Seq[Double]], maxNotInclusive: Int, bw: Double = 0.5): Seq[Seq[Double]] = {
    // Should be transposed -- time domain column vecs
    val in = inTemp.map(Matrix.toDenseMatrixFrom1D(_).t.toDenseVector.toArray.toSeq)
    val tempResult = toSeq(signal.filterLP(toDenseVector(in.flatten), omega = bw, overhang = OptOverhang.PreserveLength, taps = 512))
    val resultNotTransposed = tempResult.map { case x =>
      val temp = math.round(x)
      val out =
        if (temp >= maxNotInclusive) maxNotInclusive - 1
        else if (temp < -maxNotInclusive) -maxNotInclusive
        else temp
      out.toDouble
    }.grouped(in.head.length).toSeq
    // Get right row/col order
    resultNotTransposed.map(Matrix.toDenseMatrixFrom1D(_).t.toDenseVector.toArray.toSeq)
  }
}

class SystolicMatMulTester[T <: Data:RealBits](testMod: TestModule[SystolicMatMul[T]], ins: Option[Seq[Seq[Double]]] = None) extends DspTester(testMod) {
  val tDut = testMod.dut
  val n = tDut.n
  val isLit = tDut.litSeq.nonEmpty
  val tvs = if (ins.nonEmpty) ins.get else MatMulTests.generateSimpleInputs(n)
  for (i <- 0 until n) {
    // Assumes inputs held externally (otherwise need additional register)
    tvs(i % tvs.length).zipWithIndex foreach { case (value, idx) =>
      poke(MatMulTests.getElement[T](testMod.getIO("a"), idx), value)
      if (!isLit) poke(MatMulTests.getElement[T](testMod.getIO("b"), idx), value)
    }
    poke(testMod.getIO("start"), 1)
    step(1)
    poke(testMod.getIO("start"), 0)
    while (peek(testMod.getIO("done")) == 0) {
      step(1)
    }
    val breezeMatrix = MatMulTests.convertToDenseMatrix(tvs(i % tvs.length))
    val expected =
      if (isLit) MatMulTests.convertToDenseMatrix(tDut.litSeq) * breezeMatrix
      else breezeMatrix * breezeMatrix
    MatMulTests.convertToSeq(expected).zipWithIndex foreach { case (value, idx) =>
      expect(MatMulTests.getElement[T](testMod.getIO("out"), idx), value)
    }
  }
}

class SystolicMatMulSpec extends FlatSpec with Matchers {

  val intBits = 12
  val n = 8

  val len = n * n
  val maxW = (1 << (intBits - 1))
  val minW = -(1 << (intBits - 1))

  val maxT = (1 << (intBits - 2)) + 1
  val minT = -(1 << (intBits - 2)) - 1

  val maxM = (maxW + maxT) / 2
  val minM = (minW + minT) / 2

  val inIWide = Interval(range"[${minW}, ${maxW}).0")
  val inIThin = Interval(range"[${minT}, ${maxT}).0")
  val inIMed = Interval(range"[${minM}, ${maxM}).0")
  val outI = Interval(range"[?, ?].0")
  val inF = FixedPoint(intBits.W, 0.BP)
  val outF = FixedPoint(UnknownWidth(), 0.BP)

  val randomTVsShortW = Some(MatMulTests.generateRandomInputs(n, len, maxNotInclusive = maxW))
  val randomTVsShortT = Some(MatMulTests.generateRandomInputs(n, len, maxNotInclusive = maxT))
  val randomTVsShortM = Some(MatMulTests.generateRandomInputs(n, len, maxNotInclusive = maxM))

  behavior of "Systolic Matrix Multiplication"

  it should "properly multiply - FixedPoint" in {
    val name = s"SMatMulF${n}x${n}x${intBits}"
    DspContext.withTrimType(NoTrim) {
      dsptools.Driver.execute(() => new TestModule(() => new SystolicMatMul(inF, outF, n), name = name), IATest.options(name, backend = "verilator", fixTol = 0)) {
        c => new SystolicMatMulTester(c, randomTVsShortW)
      } should be(true)
    }
  }

  it should "properly multiply - Interval Wide" in {
    val name = s"SMatMulIWide${n}x${n}x${intBits}"
    DspContext.withTrimType(NoTrim) {
      dsptools.Driver.execute(() => new TestModule(() => new SystolicMatMul(inIWide, outI, n), name = name), IATest.options(name, backend = "verilator", fixTol = 0)) {
        c => new SystolicMatMulTester(c, randomTVsShortW)
      } should be(true)
    }
  }

  it should "properly multiply - Interval Thin" in {
    val name = s"SMatMulIThin${n}x${n}x${intBits}"
    DspContext.withTrimType(NoTrim) {
      dsptools.Driver.execute(() => new TestModule(() => new SystolicMatMul(inIThin, outI, n), name = name), IATest.options(name, backend = "verilator", fixTol = 0)) {
        c => new SystolicMatMulTester(c, randomTVsShortT)
      } should be(true)
    }
  }

  it should "properly multiply - Interval Medium" in {
    val name = s"SMatMulIMed${n}x${n}x${intBits}"
    DspContext.withTrimType(NoTrim) {
      dsptools.Driver.execute(() => new TestModule(() => new SystolicMatMul(inIMed, outI, n), name = name), IATest.options(name, backend = "verilator", fixTol = 0)) {
        c => new SystolicMatMulTester(c, randomTVsShortM)
      } should be(true)
    }
  }
}

class SystolicDCTMatMulSpec extends FlatSpec with Matchers {

  val intBits = 16 // 4 // 8 // 12 // 16
  val correction = 17 // 5 // 9 // 13 // 17
  val n = 8
  val numTests = 500
  val bp = 8

  val len = n * n
  val maxW = (1 << (intBits - 1))
  val minW = -(1 << (intBits - 1))

  val maxT = (1 << (intBits - 2)) + 1
  val minT = -(1 << (intBits - 2)) - 1

  val maxM = (maxW + maxT) / 2
  val minM = (minW + minT) / 2

  val inIWide = Interval(range"[${minW}, ${maxW}).0")
  val inIThin = Interval(range"[${minT}, ${maxT}).0")
  val inIMed = Interval(range"[${minM}, ${maxM}).0")
  val outI = Interval(range"[?, ?].${bp}")
  val inF = FixedPoint(intBits.W, 0.BP)
  val outF = FixedPoint(UnknownWidth(), bp.BP)
  val litBP = Some(bp)

  val litSeq = MatMulTests.dct(n)

  val randomTVs = Some(MatMulTests.generateRandomInputs(n, numTests, maxNotInclusive = maxW))
  val randomTVsShortW = Some(MatMulTests.generateRandomInputs(n, len, maxNotInclusive = maxW))
  val randomTVsShortT = Some(MatMulTests.generateRandomInputs(n, len, maxNotInclusive = maxT))
  val randomTVsShortM = Some(MatMulTests.generateRandomInputs(n, len, maxNotInclusive = maxM))
  val filteredRandomTVs = Some(MatMulTests.filter(randomTVs.get, bw = 0.25, maxNotInclusive = maxW))

  behavior of "Systolic DCT Matrix Multiplication"
/*
  it should "properly multiply - FixedPoint - DCT Lit" in {
    val name = s"SDCTF${n}x${n}x${intBits}"
    DspContext.withTrimType(NoTrim) {
      dsptools.Driver.execute(() => new TestModule(() => new SystolicMatMul(inF, outF, n, litSeq, litBP), name = name), IATest.options(name, backend = "verilator", fixTol = correction)) {
        c => new SystolicMatMulTester(c, randomTVsShortW)
      } should be(true)
    }
  }

  it should "properly multiply - Interval Wide - DCT Lit" in {
    val name = s"SDCTIWide${n}x${n}x${intBits}"
    DspContext.withTrimType(NoTrim) {
      dsptools.Driver.execute(() => new TestModule(() => new SystolicMatMul(inIWide, outI, n, litSeq, litBP), name = name), IATest.options(name, backend = "verilator", fixTol = correction)) {
        c => new SystolicMatMulTester(c, randomTVsShortW)
      } should be(true)
    }
  }

  it should "properly multiply - Interval Thin - DCT Lit" in {
    val name = s"SDCTIThin${n}x${n}x${intBits}"
    DspContext.withTrimType(NoTrim) {
      dsptools.Driver.execute(() => new TestModule(() => new SystolicMatMul(inIThin, outI, n, litSeq, litBP), name = name), IATest.options(name, backend = "verilator", fixTol = correction)) {
        c => new SystolicMatMulTester(c, randomTVsShortT)
      } should be(true)
    }
  }

  it should "properly multiply - Interval Medium - DCT Lit" in {
    val name = s"SDCTIMed${n}x${n}x${intBits}"
    DspContext.withTrimType(NoTrim) {
      dsptools.Driver.execute(() => new TestModule(() => new SystolicMatMul(inIMed, outI, n, litSeq, litBP), name = name), IATest.options(name, backend = "verilator", fixTol = correction)) {
        c => new SystolicMatMulTester(c, randomTVsShortM)
      } should be(true)
    }
  }
*/
  it should "properly multiply - Interval Wide - DCT Lit - RANDOM UNFILTERED" in {
    val name = s"RandomSDCTIWide${n}x${n}x${intBits}"
    DspContext.withTrimType(NoTrim) {
      dsptools.Driver.executeWithBitReduction(() => new TestModule(() => new SystolicMatMul(inIWide, outI, n, litSeq, litBP), name = name), IATest.options(name, backend = "firrtl", fixTol = correction)) {
        c => new SystolicMatMulTester(c, randomTVs)
      } should be(true)
    }
  }

  it should "properly multiply - Interval Wide - DCT Lit - RANDOM FILTERED" in {
    val name = s"FRandomSDCTIWide${n}x${n}x${intBits}"
    DspContext.withTrimType(NoTrim) {
      dsptools.Driver.executeWithBitReduction(() => new TestModule(() => new SystolicMatMul(inIWide, outI, n, litSeq, litBP), name = name), IATest.options(name, backend = "firrtl", fixTol = correction)) {
        c => new SystolicMatMulTester(c, filteredRandomTVs)
      } should be(true)
    }
  }

  println(s"MinW: $minW MaxW: $maxW")
  println(s"MinT: $minT MaxT: $maxT")
  println(s"MinM: $minM MaxM: $maxM")
}