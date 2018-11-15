// This implements a tester for DspComplex FixedPoint based StochasticRound 
// bku Nov 15 2018

package demo_pkg

import chisel3._

import dsptools.numbers.{DspComplex, DspComplexRing}
import dsptools.numbers.implicits._
import chisel3.core.{FixedPoint}
import dsptools.{NoTrim, RoundHalfUp, StochasticRound}
import dsptools.{DspTester, DspTesterOptions, DspTesterOptionsManager, DspContext, TrimType, Grow, Wrap, OverflowType}
import iotesters.TesterOptions

import org.scalatest.{Matchers, FlatSpec}

// Generic functional class, parametrized with different combinational operations
class ComplexFunctional[A <: Data:DspComplexRing, B <: Data:DspComplexRing] (inType:A, outType:B, op:String, pipes:Int, rnd:TrimType, ovf:OverflowType ) extends Module {

  val io = IO (new Bundle {
    val a   = Input(inType)
    val b   = Input(inType)
    val c   = Input(inType)
    val res = Output(outType)
  })

  val times  = dsptools.numbers.DspComplexRingImpl.times // How to resolve UInt/SInt umbiguity here ?
  

  val tmp = op match {

    case "mul"  => 
      DspContext.alter(DspContext.current.copy(trimType = rnd, numMulPipes = pipes)) { 
        times(io.a, io.b)
      }

    case "add"  =>
      DspContext.alter(DspContext.current.copy(trimType = rnd, numAddPipes = pipes, overflowType = ovf)) { 
        plus(io.a, io.b)
      }
    
    case "sub"  =>
      DspContext.alter(DspContext.current.copy(trimType = rnd, numAddPipes = pipes, overflowType = ovf)) { 
        minus(io.a, io.b)
      }
   
    case _ => io.a + io.b //throw DspException ("Unknown operation provided")
  }

  io.res := tmp

}


class ComplexFunctionalTester[A <: Data:DspComplexRing](c: Functional[A,A], op:String) extends DspTester(c) with TestCommon with DspArithmetic {
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


class ComplexRoundingSpec extends FlatSpec with Matchers with DspArithmetic {

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

  it should "Mul with Stochastic for DspComplex" in {
    val op  = "mul"
    
    dsptools.Driver.execute(() => new ComplexFunctional(cmpInType, cmpOutType, op, pipeDepth, roundType, overflow), opts) { 
      c => new ComplexFunctionalTester(c, op)
    } should be(true)
  }
  
}
