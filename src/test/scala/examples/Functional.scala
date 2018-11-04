// This implements a test suite for different rounding modes with all DspTools Ring operations 
// bku Oct 26 2018

package demo_pkg

import chisel3._

import dsptools.numbers.{Ring, DspReal, DspComplex, DspComplexRing}
import dsptools.numbers.implicits._
import chisel3.core.{FixedPoint}
import dsptools.{NoTrim, RoundHalfUp, StochasticRound}
import dsptools.{DspTester, DspTesterOptions, DspTesterOptionsManager, DspContext, TrimType, Grow, Wrap, OverflowType}
import iotesters.TesterOptions

import org.scalatest.{Matchers, FlatSpec}

// Reference operations
trait DspArithmetic {
  
  val realInType    = new DspReal
  val realOutType   = new DspReal

  val fixedInType   = FixedPoint(16.W, 10.BP)
  val fixedOutType  = FixedPoint(20.W, 12.BP)
  
  val cmpInType     = DspComplex(fixedInType , fixedInType)
  val cmpOutType    = DspComplex(fixedOutType, fixedOutType)

  val pipeDepth     = 2
  val bitsPrecision = 4
  
  val roundType     = StochasticRound 
  val overflow      = Grow
  
}

// Generic functional class, parametrized with different combinational operations
class Functional[A <: Data:Ring, B <: Data:Ring] (inType:A, outType:B, op:String, pipes:Int, rnd:TrimType, ovf:OverflowType ) extends Module {

  val io = IO (new Bundle {
    val a   = Input(inType)
    val b   = Input(inType)
    val c   = Input(inType)
    val res = Output(outType)
  })

  val tmp = op match {

    case "mul"  => 
      DspContext.alter(DspContext.current.copy(trimType = rnd, numMulPipes = pipes)) { 
        io.a * io.b
      }

    case "add"  =>
      DspContext.alter(DspContext.current.copy(trimType = rnd, numAddPipes = pipes, overflowType = ovf)) { 
        io.a + io.b
      }
    
    case "sub"  =>
      DspContext.alter(DspContext.current.copy(trimType = rnd, numAddPipes = pipes, overflowType = ovf)) { 
        io.a - io.b
      }
   
    case _ => io.a + io.b //throw DspException ("Unknown operation provided")
  }

  io.res := tmp

}

// Test setup
trait TestCommon {

  val totalTests = 2
  
  val rand  = scala.util.Random
  val range = 10
  
}

// Test top
class FunctionalTester[A <: Data:Ring, B <: Data:Ring](c: Functional[A,B], op:String) extends DspTester(c) with TestCommon with DspArithmetic {
  val uut  = c.io

  var x = 0.0
  var y = 0.0
  var z = 0.0
    
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

class ComplexFunctionalTester[A <: Data:Ring](c: Functional[A,A], op:String) extends DspTester(c) with TestCommon with DspArithmetic {
  val uut  = c.io

  val zero = 0.U.asInstanceOf[this.type]

  var x = DspComplex.zero
  var y = DspComplex.zero
  var z = DspComplex.zero
    
  (1 to totalTests).foreach { i =>

    //x = 
    //y = 
    //z = 

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

    //expect (uut.res, expResult)
  }

}


class RoundingSpec extends FlatSpec with Matchers with DspArithmetic {

  println (s"Running a Functional test for rounding: $roundType")

 
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
    val op  = "mul"
  
    dsptools.Driver.execute(() => new Functional(fixedInType, fixedOutType, op, pipeDepth, roundType, overflow), opts) { 
      c => new FunctionalTester(c, op)
    } should be(true)
  }
  
  it should "Mul with Stochastic Round for DspReal" in {
    val op  = "mul"
  
    dsptools.Driver.execute(() => new Functional(realInType, realOutType, op, pipeDepth, roundType, overflow), opts) { 
      c => new FunctionalTester(c, op)
    } should be(true)
  }
  
  it should "Mul with Stochastic for DspComplex" in {
    val op  = "mul"
    
    dsptools.Driver.execute(() => new Functional(cmpInType, cmpOutType, op, pipeDepth, roundType, overflow), opts) { 
      c => new ComplexFunctionalTester(c, op)
    } should be(true)
  }
  
//  it should "Mul with Stochastic Round for FixedPoint with Verilator" in {
//  
//    dsptools.Driver.execute(() => new Functional(inType, outType, pipeDepth, roundType, mul), vopts) { 
//      c => new FunctionalTester(c, "mul")
//    } should be(true)
//  }
  
//  it should "Add with Stochastic Round for FixedPoint" in {
//    val op  = "add"
//  
//    dsptools.Driver.execute(() => new Functional(inType, outType, op, pipeDepth, roundType, overflow), opts) { 
//      c => new FunctionalTester(c, op)
//    } should be(true)
//  }
//
//  it should "Sub with Stochastic Round for FixedPoint" in {
//    val op  = "sub"
//    
//    dsptools.Driver.execute(() => new Functional(inType, outType, op, pipeDepth, roundType, overflow), opts) { 
//      c => new FunctionalTester(c,op)
//    } should be(true)
//  }
  
//  it should "Madd with Stochastic Round for FixedPoint" in {
//    
//    dsptools.Driver.execute(() => new Functional(inType, outType, pipeDepth, roundType, madd), opts) { 
//      c => new FunctionalTester(c,"madd")
//    } should be(true)
//  }
//  
//  it should "Msub with Stochastic Round for FixedPoint" in {
//    
//    dsptools.Driver.execute(() => new Functional(inType, outType, pipeDepth, roundType, msub), opts) { 
//      c => new FunctionalTester(c,"msub")
//    } should be(true)
//  }
  
 
  

}

