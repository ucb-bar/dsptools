package dsptools.toys

import breeze.math.Complex
import chisel3._
import dsptools.intervals.tests.IATest
import dsptools.numbers._
import dsptools.{NoTrim, DspContext, DspTester}
import chisel3.internal.firrtl.IntervalRange

import chisel3.experimental._
import generatortools.testing.TestModule

import org.scalatest.{Matchers, FlatSpec}

class FilterIO[T <: Data:Ring:ConvertableTo](genI: => T, genO: => T) extends Bundle {
  val in = Input(genI)
  val out = Output(genO)
}

// y[n] = sum { b_i * x[n - i] }
@chiselName
class FIR[T <: Data:Ring:ConvertableTo](genI: => T, genO: => T, bp: Int, pipes: Int, coeffs: Seq[Double]) extends Module {
  val io = IO(new FilterIO(genI, genO))
  val newContext = DspContext.current.copy(trimType = NoTrim, numMulPipes = pipes, binaryPoint = Some(bp))
  DspContext.alter(newContext) {
    val taps = coeffs.tail.scanLeft(io.in)((in, _) => RegNext(in, init = Ring[T].zero))
    val cs = coeffs map (c => ConvertableTo[T].fromDouble(c))
    io.out := taps zip (cs) map { case (t, c) => t context_* c } reduce (_ context_+ _)
  }
}

/*
class FIR[T <: Data:Ring:ConvertableTo](genI: => T, genO: => T, bp: Int, mp: Int, ap: Int, val coeffs: Seq[Double]) extends Module {
  val io = IO(new FilterIO(genI, genO))
  DspContext.alter(DspContext.current.copy(trimType = NoTrim, numMulPipes = mp, numAddPipes = ap, binaryPoint = Some(bp))) {
    val taps = coeffs.tail.scanLeft(io.in)((in, _) => RegNext(in, init = ConvertableTo[T].fromDouble(0.0)))
    val coeffsT = coeffs map (c => ConvertableTo[T].fromDouble(c))
    io.out := taps zip (coeffsT) map { case (t, c) => t context_* c } reduce (_ context_+ _)
  }
}
*/

class FIRFilterTester[T <: Data:Ring:ConvertableTo](testMod: TestModule[FIR[T]], tvs: Seq[Double], coeffs: Seq[Double]) extends DspTester(testMod) {
  val tDut = testMod.dut

  tDut.io.in match {
    case i: Interval =>
      poke(testMod.getIO("in"), 0.0)
    case c: DspComplex[_] =>
      poke(testMod.getIO("in").asInstanceOf[DspComplex[_]], Complex(0.0, 0.0))
  }

  reset(5)
  step(1)

  for (idx <- tvs.indices) {
    tDut.io.in match {
      case i: Interval =>
        poke(testMod.getIO("in"), tvs(idx))
      case c: DspComplex[_] =>
        poke(testMod.getIO("in").asInstanceOf[DspComplex[_]], Complex(tvs(idx), -tvs(idx)))
    }
    // Accounts for 1 cycle (hardcoded) multiplication latency
    if (idx >= coeffs.length) {
      // Earliest is the right-most in the chain
      val currTaps = tvs.slice(idx - coeffs.length, idx).reverse
      tDut.io.out match {
        case i: Interval =>
          val exp = currTaps.zip(coeffs) map { case (t, c) => t * c } reduce (_ + _)
          expect(testMod.io("out"), exp)
        case c: DspComplex[_] =>
          val exp = currTaps.zip(coeffs) map { case (t, c) => Complex(t, -t) * c } reduce (_ + _)
          expect(testMod.io("out").asInstanceOf[DspComplex[_]], exp)
      }
    }
    step(1)
  }
}
/*
class FIRFilterSpec extends FlatSpec with Matchers {
  val inI = Interval(range"[-16, 16).2")
  val outI = Interval(range"[?, ?].4")
  val coeffs = inI.range.getPossibleValues.take(64)
  // TODO: Separate function!
  val randomTVs = MatMulTests.generateRandomInputs(math.sqrt(coeffs.length).toInt, 20, maxNotInclusive = 15).flatten

  behavior of "FIR Filter"

  it should "properly filter -- Interval" in {
    val name = s"FIRFilterI"
    dsptools.Driver.execute(() => new TestModule(() => new FIR(inI, outI, bp = 2, mp = 1, ap = 0, coeffs), name = name), IATest.options(name, backend = "firrtl", fixTol = 1)) {
        c => new FIRTester(c, randomTVs, coeffs)
    } should be(true)
  }

  it should "properly filter -- Complex" in {
    val name = s"FIRFilterC"
    dsptools.Driver.execute(() => new TestModule(() => new FIR(DspComplex(inI), DspComplex(outI), bp = 2, mp = 1, ap = 0, coeffs), name = name), IATest.options(name, backend = "firrtl", fixTol = 1)) {
      c => new FIRTester(c,randomTVs, coeffs)
    } should be(true)
  }
}
*/

class FIRFilterSpec extends FlatSpec with Matchers {
  val inI = Interval(range"[-16, 16).2")
  val outI = Interval(range"[?, ?].4")
  val coeffs = inI.range.getPossibleValues.take(64)
  // TODO: Separate function!
  val randomTVs = MatMulTests.generateRandomInputs(math.sqrt(coeffs.length).toInt, 20, maxNotInclusive = 15).flatten

  behavior of "FIR Filter"

  it should "properly filter -- Interval" in {
    val name = s"FIRFilterI"
    dsptools.Driver.execute(() => new TestModule(() => new FIR(Interval(range"[-16, 16).2"), Interval(range"[?, ?].4"), bp = 2, pipes = 1, coeffs), name = name), IATest.options(name, backend = "firrtl", fixTol = 1)) {
      c => new FIRFilterTester(c, randomTVs, coeffs)
    } should be(true)
  }

  it should "properly filter -- Complex" in {
    val name = s"FIRFilterC"
    dsptools.Driver.execute(() => new TestModule(() => new FIR(DspComplex(Interval(range"[-16, 16).2")), DspComplex(Interval(range"[?, ?].4")), bp = 2, pipes = 1, coeffs), name = name), IATest.options(name, backend = "firrtl", fixTol = 1)) {
      c => new FIRFilterTester(c,randomTVs, coeffs)
    } should be(true)
  }
}