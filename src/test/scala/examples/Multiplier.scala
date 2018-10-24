package demo_pkg

import chisel3._

import dsptools.numbers.{Ring, DspReal, DspComplex}
import dsptools.numbers.implicits._

import chisel3.core.{FixedPoint => FP}
import dsptools.{DspTester, DspTesterOptions, DspTesterOptionsManager, DspContext, TrimType}
import dsptools.{NoTrim, RoundHalfUp, StochasticRound}

import org.scalatest.{Matchers, FlatSpec}

class Multiplier[A <: Data:Ring, B <: Data:Ring] (inType:A, outType:B, mulPipes:Int, rnd:TrimType) extends Module {

  val io = IO (new Bundle {
    val a   = Input(inType)
    val b   = Input(inType)
    val res = Output(outType)
  })

  val mres  = Reg(outType.cloneType)  // mul result
  val rndModes  = Seq (NoTrim, RoundHalfUp, StochasticRound)
  
  // Compute block
  DspContext.alter(DspContext.current.copy(trimType = rnd, numMulPipes = mulPipes)) { 
    mres := io.a context_* io.b
  }
 
  io.res := mres

}

class ComplexMultiplier[A <: Data:Ring, B <: Data:Ring] (inType:A, outType:B, mulPipes:Int, rnd:TrimType) extends Module {

  val io = IO (new Bundle {
    val a   = Input(DspComplex(inType, inType))
    val b   = Input(DspComplex(inType, inType))
    val res = Output(DspComplex(outType, outType))
  })

  val mres  = Reg(outType.cloneType)  // mul result
  val rndModes  = Seq (NoTrim, RoundHalfUp, StochasticRound)
  
  // Compute block
  DspContext.alter(DspContext.current.copy(trimType = rnd, numMulPipes = mulPipes)) { 
    mres := io.a context_* io.b
  }
 
  io.res := mres

}

trait TestCommon {

  val totalTests = 1
  
  val rand  = scala.util.Random
  val range = 10
  
  var x = 0.0
  var y = 0.0

}

class MultiplierTester[A <: Data:Ring, B <: Data:Ring](c: Multiplier[A,B]) extends DspTester(c) with TestCommon {
  val uut  = c.io
  
  (1 to totalTests).foreach { i =>

    x = rand.nextDouble * range
    y = rand.nextDouble * range

    poke(uut.a, x)
    poke(uut.b, y)
    step (3)
    expect (uut.res, x*y)
  }

}

class ComplexMultiplierTester[A <: Data:Ring, B <: Data:Ring](c: ComplexMultiplier[A,B]) extends DspTester(c) with TestCommon {
  val uut  = c.io
 
  var xImg  = 0.0
  var yImg  = 0.0
  
  (1 to totalTests).foreach { i =>

    x     = rand.nextDouble * range
    y     = rand.nextDouble * range
    xImg  = rand.nextDouble * range
    yImg  = rand.nextDouble * range

    poke(uut.a.real, x)
    poke(uut.a.imag, xImg)
    poke(uut.b.real, y)
    poke(uut.b.imag, yImg)
    step (3)
    expect (uut.res, (x + xImg) * (y+ yImg))
  }
  
}

class MultiplierSpec extends FlatSpec with Matchers  {

  val inType  = FP(16.W, 8.BP)
  val outType = FP(20.W, 12.BP)

  val complexInType   = DspComplex(inType.cloneType, inType.cloneType)
  val complexOutType  = DspComplex(outType.cloneType, outType.cloneType)

  val opts = new DspTesterOptionsManager {
  
    dspTesterOptions = DspTesterOptions(
      fixTolLSBs = 2,
      genVerilogTb = false,
      isVerbose = true
    )
  }
  
  behavior of "implementation"

  //it should "Rnd Half UP for FixedPoint" in {
  //  //dsptools.Driver.execute (() => new Multiplier(inType, outType, 2), Array("--backend-name", "verilator")) {
  //  dsptools.Driver.execute(() => new Multiplier(inType, outType, 2, RoundHalfUp), opts) { 
  //    c => new MultiplierTester(c)
  //  } should be(true)
  //}
  it should "Rnd Stochastic for UInt" in {
    //dsptools.Driver.execute (() => new Multiplier(inType, outType, 2), Array("--backend-name", "verilator")) {
    dsptools.Driver.execute(() => new Multiplier(DspReal, DspReal, 2, StochasticRound), opts) { 
      c => new MultiplierTester(c)
    } should be(true)
  }
  
  it should "Rnd Stochastic for DspReal" in {
    //dsptools.Driver.execute (() => new Multiplier(inType, outType, 2), Array("--backend-name", "verilator")) {
    dsptools.Driver.execute(() => new Multiplier(DspReal, DspReal, 2, StochasticRound), opts) { 
      c => new MultiplierTester(c)
    } should be(true)
  }
  
  it should "Rnd Stochastic for FixedPoint" in {
    //dsptools.Driver.execute (() => new Multiplier(inType, outType, 2), Array("--backend-name", "verilator")) {
    dsptools.Driver.execute(() => new Multiplier(inType, outType, 2, StochasticRound), opts) { 
      c => new MultiplierTester(c)
    } should be(true)
  }
  
  it should "Rnd Stochastic for Complex" in {
    //dsptools.Driver.execute (() => new Multiplier(inType, outType, 2), Array("--backend-name", "verilator")) {
    dsptools.Driver.execute(() => new ComplexMultiplier(complexInType, complexOutType, 2, StochasticRound), opts) { 
      c => new ComplexMultiplierTester(c)
    } should be(true)
  }

}

