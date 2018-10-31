// This implements a test suite for different rounding modes with all DspTools Ring operations 
// bku Oct 26 2018

package demo_pkg

import chisel3._

import dsptools.numbers.{Ring, DspReal, DspComplex}
import dsptools.numbers.implicits._
import chisel3.core.{FixedPoint}
import dsptools.{NoTrim, RoundHalfUp, StochasticRound}
import dsptools.{DspTester, DspTesterOptions, DspTesterOptionsManager, DspContext, TrimType}
import iotesters.TesterOptions

import org.scalatest.{Matchers, FlatSpec}

// Reference operations
trait DspArithmetic {
  
  //val inType  = DspReal
  //val outType = DspReal
  val inType  = FixedPoint(16.W, 10.BP)
  val outType = FixedPoint(20.W, 12.BP)
  
  val complexInType   = DspComplex(inType, inType)
  val complexOutType  = DspComplex(outType, outType)

  val pipeDepth  = 2
  val bitsPrecision = 4
  
  val roundType  = StochasticRound
  
  // DSP Methods to test
  def mul[T<:Data with Num[T]]  = (x:T, y:T, z:T) =>  x * y
  def add[T<:Data with Num[T]]  = (x:T, y:T, z:T) =>  x + y
  def sub[T<:Data with Num[T]]  = (x:T, y:T, z:T) =>  x - y
  def div[T<:Data with Num[T]]  = (x:T, y:T, z:T) =>  x / y
  def mod[T<:Data with Num[T]]  = (x:T, y:T, z:T) =>  x % y
  def madd[T<:Data with Num[T]] = (x:T, y:T, z:T) =>  x * y + z
  def msub[T<:Data with Num[T]] = (x:T, y:T, z:T) =>  x * y - z

}

// Generic functional class, parametrized with different combinational operations
class Functional[A <: Data, B <: Data] (inType:A, outType:B, pipes:Int, rnd:TrimType, op:(A,A,A) => A) extends Module {

  val io = IO (new Bundle {
    val a   = Input(inType)
    val b   = Input(inType)
    val c   = Input(inType)
    val res = Output(outType)
  })

  val mres  = Reg(outType)
 
  // TBD: Add Dsp Pipelining tests if required
  DspContext.alter(DspContext.current.copy(trimType = rnd)) { 
    mres := op(io.a, io.b, io.c) 
  }
 
  io.res := mres

}

// Test setup
trait TestCommon {

  val totalTests = 2
  
  val rand  = scala.util.Random
  val range = 10
  
  var x = 0.0
  var y = 0.0
  var z = 0.0

}

// Test top
class FunctionalTester[A <: Data, B <: Data](c: Functional[A,B], op:String) extends DspTester(c) with TestCommon with DspArithmetic {
  val uut  = c.io

    
  (1 to totalTests).foreach { i =>

    x = rand.nextDouble * range
    y = rand.nextDouble * range
    z = rand.nextDouble * range

    poke(uut.a, x)
    poke(uut.b, y)
    poke(uut.c, z)
    step (3)

    val expResult = op match {

      case "mul"  => x * y
      case "add"  => x + y
      case "sub"  => x - y
      case "madd" => x * y + z
      case "msub" => x * y - z 
      case "div"  => x / y // NYI
      case "mod"  => x % y // NYI
      case _      => x + y

    }

    expect (uut.res, expResult)
  }

}


class RoundingSpec extends FlatSpec with Matchers with DspArithmetic {

  println (s"Running a Functional test for data type: $inType and rounding: $roundType")

 
  // Default backend 
  val opts = new DspTesterOptionsManager {
    dspTesterOptions = DspTesterOptions(
      fixTolLSBs = bitsPrecision,
      genVerilogTb = false,
      isVerbose = true
    )
  }
  
  // Same with Verilator backend
  val vopts = new DspTesterOptionsManager {
    dspTesterOptions = DspTesterOptions(
      fixTolLSBs = bitsPrecision,
      genVerilogTb = false,
      isVerbose = true
    )
    testerOptions = TesterOptions(
      backendName = "verilator"
    )
  }

  
  behavior of "implementation"

  it should "Mul with Stochastic Round for FixedPoint" in {
  
    dsptools.Driver.execute(() => new Functional(inType, outType, pipeDepth, roundType, mul), opts) { 
      c => new FunctionalTester(c, "mul")
    } should be(true)
  }
  
  it should "Mul with Stochastic Round for FixedPoint with Verilator" in {
  
    dsptools.Driver.execute(() => new Functional(inType, outType, pipeDepth, roundType, mul), vopts) { 
      c => new FunctionalTester(c, "mul")
    } should be(true)
  }
  
  it should "Add with Stochastic Round for FixedPoint" in {
  
    dsptools.Driver.execute(() => new Functional(inType, outType, pipeDepth, roundType, add), opts) { 
      c => new FunctionalTester(c, "add")
    } should be(true)
  }
  
  it should "Sub with Stochastic Round for FixedPoint" in {
    
    dsptools.Driver.execute(() => new Functional(inType, outType, pipeDepth, roundType, sub), opts) { 
      c => new FunctionalTester(c,"sub")
    } should be(true)
  }
  
  it should "Madd with Stochastic Round for FixedPoint" in {
    
    dsptools.Driver.execute(() => new Functional(inType, outType, pipeDepth, roundType, madd), opts) { 
      c => new FunctionalTester(c,"madd")
    } should be(true)
  }
  
  it should "Msub with Stochastic Round for FixedPoint" in {
    
    dsptools.Driver.execute(() => new Functional(inType, outType, pipeDepth, roundType, msub), opts) { 
      c => new FunctionalTester(c,"msub")
    } should be(true)
  }
  
  // FIXME - how to instantiate other types ???
  
  //it should "Mul with Stochastic Round for Real" in {
  //  val rtype  = DspReal
  //  
  //
  //  dsptools.Driver.execute(() => new Functional(rtype, rtype, pipeDepth, roundType, mul), opts) { 
  //    c => new FunctionalTester(c, "mul")
  //  } should be(true)
  //}
 
 
  //it should "Add with Stochastic for DspComplex" in {
  //  dsptools.Driver.execute(() => new Functional(complexInType, complexOutType, pipeDepth, roundType, add), opts) { 
  //    c => new FunctionalTester(c, "add")
  //  } should be(true)
  //}
  

}

