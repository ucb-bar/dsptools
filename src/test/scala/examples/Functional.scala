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

// Generic functional class, parametrized by different combinational operations
class Functional[A <: Data:Ring, B <: Data:Ring] (inType:A, outType:B, mulPipes:Int, rnd:TrimType, op:(A,A) => A) extends Module {

  val io = IO (new Bundle {
    val a   = Input(inType)
    val b   = Input(inType)
    val res = Output(outType)
  })

  val mres  = Reg(outType.cloneType)  // mul result
  
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

class RoundingTester[A <: Data:Ring, B <: Data:Ring](c: Functional[A,B]) extends DspTester(c) with TestCommon {
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


class RoundingSpec extends FlatSpec with Matchers  {

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

  it should "Mul with Rnd Half UP for FixedPoint" in {
    //def mul[T<:Data with Num[T]](x:T, y:T) = x * y
    //def mul[T<:Data:Ring](x:T, y:T) = x context_ y
    def mul[T<:Data:Ring](x:T, y:T) = x * y
    
    dsptools.Driver.execute(() => new Functional(inType, outType, 2, RoundHalfUp, mul(inType, inType)), opts) { 
      c => new RoundingTester(c)
    } should be(true)
  }
  //it should "Rnd Stochastic for Complex" in {
  //  dsptools.Driver.execute(() => new Functional(complexInType, complexOutType, 2, StochasticRound), opts) { 
  //    c => new RoundingTester(c)
  //  } should be(true)
  //}
  

}

