package dsptools.toys

import breeze.math.Complex
import chisel3._
import dsptools.intervals.tests.IATest
import dsptools.numbers._
import breeze.linalg.{DenseVector, DenseMatrix}
import generatortools.io.CustomBundle
import dsptools.{NoTrim, DspContext, DspTester}

import chisel3.experimental._
import generatortools.testing.TestModule
import chisel3.internal.firrtl.{UnknownWidth, KnownWidth, IntervalRange, KnownBinaryPoint}

import org.scalatest.{Matchers, FlatSpec}

import scala.util.Random

class FilterIO[T <: Data:Ring:ConvertableTo](genI: => T, genO: => T) extends Bundle {
  val in = Input(genI)
  val out = Output(genO)
}

@chiselName
class FIRFilter[T <: Data:Ring:ConvertableTo](genI: => T, genO: => T, bp: Int, mp: Int, ap: Int, val coeffs: Seq[Double]) extends Module {
  val io = IO(new FilterIO(genI, genO))
  DspContext.alter(DspContext.current.copy(trimType = NoTrim, numMulPipes = mp, numAddPipes = ap, binaryPoint = Some(bp))) {
    val taps = (1 until coeffs.length).scanLeft(io.in)((in, _) => RegNext(in, init = ConvertableTo[T].fromDouble(0.0)))
    val coeffsT = coeffs.map(c => ConvertableTo[T].fromDouble(c))
    io.out := taps.zip(coeffsT) map { case (t, c) => t context_* c } reduce(_ context_+ _)
  }
}

class FIRFilterTester[T <: Data:Ring:ConvertableTo](testMod: TestModule[FIRFilter[T]], tvs: Seq[Double]) extends DspTester(testMod) {
  val tDut = testMod.dut
  val coeffs = tDut.coeffs
  reset(1)
  step(1)
  for (idx <- 0 until tvs.length) {
    tDut.io.in match {
      case i: Interval =>
        poke(testMod.getIO("in"), tvs(idx))
      case c: DspComplex[_] =>
        poke(testMod.getIO("in").asInstanceOf[DspComplex[_]], Complex(tvs(idx), -tvs(idx)))
    }
    // Accounts for 1 cycle multiplication latency
    if (idx >= coeffs.length) {
      val currTaps = tvs.slice(idx - coeffs.length, idx - 1)
      tDut.io.out match {
        case i: Interval =>
          val exp = currTaps.zip(coeffs) map { case (t, c) => t * c } reduce(_ + _)
          expect(testMod.io("out"), exp)
        case c: DspComplex[_] =>
          val exp = currTaps.zip(coeffs) map { case (tap, coeff) => Complex(tap, -tap) * coeff } reduce(_ + _)
          expect(testMod.io("out").asInstanceOf[DspComplex[_]], exp)
      }
    }
    step(1)
  }
}

class FIRFilterSpec extends FlatSpec with Matchers {
  val inI = Interval(range"[-16, 16).2")
  val outI = Interval(range"[?, ?].2")
  val coeffs = inI.range.getPossibleValues.take(32)
  // TODO: Separate function!
  val randomTVs = MatMulTests.generateRandomInputs(math.sqrt(coeffs.length).toInt, 10, maxNotInclusive = 15).flatten

  behavior of "FIR Filter"
/*
  it should "properly filter -- Interval" in {
    val name = s"FIRFilterI"
    dsptools.Driver.execute(() => new TestModule(() => new FIRFilter(inI, outI, bp = 2, mp = 1, ap = 0, coeffs), name = name), IATest.options(name, backend = "verilator", fixTol = 1)) {
        c => new FIRFilterTester(c, randomTVs)
    } should be(true)
  }
*/
  it should "properly filter -- Complex" in {
    val name = s"FIRFilterI"
    dsptools.Driver.execute(() => new TestModule(() => new FIRFilter(DspComplex(inI), DspComplex(outI), bp = 2, mp = 1, ap = 0, coeffs), name = name), IATest.options(name, backend = "firrtl", fixTol = 1)) {
      c => new FIRFilterTester(c,randomTVs)
    } should be(true)
  }
}