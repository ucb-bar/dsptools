package demo_pkg

import chisel3._

import dsptools.numbers.{Ring, DspComplex}
import dsptools.numbers.implicits._

import chisel3.core.{FixedPoint => FP}
import dsptools.{DspTester, DspTesterOptions, DspTesterOptionsManager, DspContext, TrimType}
import dsptools.{NoTrim, RoundHalfUp, StochasticRound}

import org.scalatest.{Matchers, FlatSpec}
import org.scalatest.prop.{PropertyChecks}

//import org.la4j.matrix._
//import java.util.Random

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
  //DspContext.withNumMulPipes(mulPipes) { 
    mres := io.a context_* io.b
  }
 
  io.res := mres

}

class MultiplierTester[A <: Data:Ring, B <: Data:Ring](c: Multiplier[A,B]) extends DspTester(c) {
  val uut  = c.io

  val a  = 1.1
  val b  = 2.33
  

  (1 to 1) foreach { i =>

    poke(uut.a, a)
    poke(uut.b, b)
    step (3)
    expect (uut.res, a*b)
  }
}

class MultiplierSpec extends FlatSpec with Matchers  {
//class MultiplierSpec extends FlatSpec with PropertyChecks  {

  val inType  = FP(18.W, 8.BP)
  val outType = FP(36.W, 20.BP)

  val complexInType   = DspComplex(inType.cloneType, inType.cloneType)
  val complexOutType  = DspComplex(outType.cloneType, outType.cloneType)

  val opts = new DspTesterOptionsManager {
  
    dspTesterOptions = DspTesterOptions(
      fixTolLSBs = 0,
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
  
  it should "Rnd Stochastic for FixedPoint" in {
    //dsptools.Driver.execute (() => new Multiplier(inType, outType, 2), Array("--backend-name", "verilator")) {
    dsptools.Driver.execute(() => new Multiplier(inType, outType, 2, StochasticRound), opts) { 
      c => new MultiplierTester(c)
    } should be(true)
  }
  
  it should "Rnd Stochastic for FixedPoint 1" in {
    //dsptools.Driver.execute (() => new Multiplier(inType, outType, 2), Array("--backend-name", "verilator")) {
    dsptools.Driver.execute(() => new Multiplier(inType, outType, 2, StochasticRound), opts) { 
      c => new MultiplierTester(c)
    } should be(true)
  }
  
  //it should "StochasticRound for Complex" in {
  //  //dsptools.Driver.execute (() => new Multiplier(inType, outType, 2), Array("--backend-name", "verilator")) {
  //  dsptools.Driver.execute(() => new Multiplier(complexInType, complexOutType, 2), opts) { 
  //    c => new MultiplierTester(c)
  //  } should be(true)
  //}

}
