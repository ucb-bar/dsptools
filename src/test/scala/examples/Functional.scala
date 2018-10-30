// This implements a test suite for different rounding modes with all DspTools Ring operations 
// bku Oct 26 2018

package demo_pkg

import chisel3._

import dsptools.numbers.{Ring, DspReal, DspComplex}
import dsptools.numbers.implicits._

import chisel3.core.{FixedPoint => FP}
import dsptools.{DspTester, DspTesterOptions, DspTesterOptionsManager, DspContext, TrimType}
import dsptools.{NoTrim, RoundHalfUp, StochasticRound}

import org.scalatest.{Matchers, FlatSpec}

trait DspArithmetic {
  
  val inType  = FP(16.W, 10.BP)
  val outType = FP(20.W, 12.BP)
  
  val complexInType   = DspComplex(inType, inType)
  val complexOutType  = DspComplex(outType, outType)

  val pipeDepth  = 2
  val roundType  = StochasticRound
  
  // DSP Methods to test
  def mul[T<:Data with Num[T]] = (x:T, y:T) =>  x * y
  def add[T<:Data with Num[T]] = (x:T, y:T) =>  x + y
  def sub[T<:Data with Num[T]] = (x:T, y:T) =>  x - y
  def div[T<:Data with Num[T]] = (x:T, y:T) =>  x / y

}

// Generic functional class, parametrized by different combinational operations
class Functional[A <: Data, B <: Data] (inType:A, outType:B, pipes:Int, rnd:TrimType, op:(A,A) => A) extends Module {

  val io = IO (new Bundle {
    val a   = Input(inType)
    val b   = Input(inType)
    val res = Output(outType)
  })

  val mres  = Reg(outType)
  
  // Compute block
  DspContext.alter(DspContext.current.copy(trimType = rnd)) { 
    mres := op(io.a, io.b) 
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

class FunctionalTester[A <: Data, B <: Data](c: Functional[A,B], op:(A,A) => A) extends DspTester(c) with TestCommon with DspArithmetic {
  val uut  = c.io

    
  (1 to totalTests).foreach { i =>

    x = rand.nextDouble * range
    y = rand.nextDouble * range

    poke(uut.a, x)
    poke(uut.b, y)
    step (3)

    val expResult = op match {

      case mul  => x * y
      case add  => x + y
      case sub  => x - y
      case div  => x / y
      case _    => x + y

    }

    expect (uut.res, expResult)
  }

}

class RoundingSpec extends FlatSpec with Matchers with DspArithmetic {

  val opts = new DspTesterOptionsManager {
  
    dspTesterOptions = DspTesterOptions(
      fixTolLSBs = 4,
      genVerilogTb = false,
      isVerbose = true
    )
  }

  
  behavior of "implementation"

  it should "Mul with Stochastic Round for FixedPoint" in {
  
    dsptools.Driver.execute(() => new Functional(inType, outType, pipeDepth, roundType, mul), opts) { 
      c => new FunctionalTester(c, mul)
    } should be(true)
  }
  
  it should "Add with Stochastic Round for FixedPoint" in {
  
    dsptools.Driver.execute(() => new Functional(inType, outType, pipeDepth, roundType, add), opts) { 
      c => new FunctionalTester(c, add)
    } should be(true)
  }
  
  it should "Sub with Stochastic Round for FixedPoint" in {
    
    dsptools.Driver.execute(() => new Functional(inType, outType, pipeDepth, roundType, sub), opts) { 
      c => new FunctionalTester(c,sub)
    } should be(true)
  }
  
  //it should "Div with Stochastic Round for FixedPoint" in {
  //
  //  
  //  dsptools.Driver.execute(() => new Functional(inType, outType, 2, StochasticRound, div), opts) { 
  //    c => new FunctionalTester(c)
  //  } should be(true)
  //}

  //it should "Rnd Stochastic for Complex" in {
  //  dsptools.Driver.execute(() => new Functional(complexInType, complexOutType, 2, StochasticRound), opts) { 
  //    c => new FunctionalTester(c)
  //  } should be(true)
  //}
  

}

