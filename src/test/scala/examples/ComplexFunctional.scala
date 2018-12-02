// This implements a tester for DspComplex FixedPoint based StochasticRound 
// bku Nov 15 2018


package demo_pkg

import chisel3._

import dsptools.numbers.{Ring, DspComplex, DspComplexRing}
import dsptools.numbers.implicits._
import chisel3.core.{FixedPoint}
import dsptools.{NoTrim, RoundHalfUp, StochasticRound}
import dsptools.{DspTester, DspTesterOptions, DspTesterOptionsManager, DspContext, TrimType, Grow, Wrap, OverflowType}
import iotesters.TesterOptions

import org.scalatest.{Matchers, FlatSpec}
import scala.language.higherKinds


import mypkg._

// Generic functional class, parametrized with different combinational operations
//trait DspDevice[C[_], A, B] extends Bundle {
trait DspDevice[C[_], A, B] {
  val inType : C[A]
  val outType: C[B]
}

sealed abstract class DspDev[A,B,C[_]] () extends Module {
  val inType : C[A]
  val outType: C[B]
}


//class ComplexFunctional (inType:DspComplex(FixedPoint(6.W, 4.BP), FixedPoint(6.W, 4.BP)), outType:DspComplex(FixedPoint(6.W, 4.BP), FixedPoint(6.W, 4.BP)), op:String, pipes:Int, rnd:TrimType, ovf:OverflowType ) extends Module {
class ComplexFunctional[A <: Data:DspComplexRing, B <:Data:DspComplexRing] (inType:A, outType:B, op:String, pipes:Int, rnd:TrimType, ovf:OverflowType ) extends Module {
  
  //val fpIn  = FixedPoint(6.W, 4.BP)
  //val fpOut = FixedPoint(6.W, 4.BP)
  //val fpIn  = FixedPoint(6.W, 4.BP)
  //val fpOut = FixedPoint(6.W, 4.BP)
  
  val CpxType = DspComplex (inType, outType)

  val io = IO (new Bundle {
    val a   = Input(CpxType)
    val b   = Input(CpxType)
    val c   = Input(CpxType)
    val res = Output(CpxType)
  })
}
//  val r = implicitly [DspComplexRing[A]]
//  //val r = implicitly [Ring[A]]
//
//  val tmp = op match {
//
//    case "mul"  => 
//      DspContext.alter(DspContext.current.copy(trimType = rnd, numMulPipes = pipes)) { 
//        r.times(io.a, io.b)
//      }
//
//    case "add"  =>
//      DspContext.alter(DspContext.current.copy(trimType = rnd, numAddPipes = pipes, overflowType = ovf)) { 
//        r.plus(io.a, io.b)
//      }
//    
//    case "sub"  =>
//      DspContext.alter(DspContext.current.copy(trimType = rnd, numAddPipes = pipes, overflowType = ovf)) { 
//        r.minus(io.a, io.b)
//      }
//   
//    case _ => r.plus(io.a, io.b) // throw DspException ("Unknown operation provided")
//  }
//
//  io.res := tmp

//}


class ComplexFunctionalTester[A <: Data:DspComplexRing, B <:Data:DspComplexRing](c: ComplexFunctional[A,B], op:String) extends DspTester(c) {
  val uut  = c.io

  val zero = 0.U.asInstanceOf[this.type]


//  var x = DspComplex.zero
//  var y = DspComplex.zero
//  var z = DspComplex.zero
//    
//  (1 to totalTests).foreach { i =>
//
//    //x = 
//    //y = 
//    //z = 
//
//    poke(uut.a, x)
//    poke(uut.b, y)
//    poke(uut.c, z)
//    step (3)
//
//    val expResult = op match {
//
//      case "mul"  => x * y
//      case "add"  => x + y
//      case "sub"  => x - y
//      case "madd" => x * y + z
//      case "msub" => x * y - z 
//      case "div"  => x / y // NYI
//      case "mod"  => x % y // NYI
//      case _      => x + y
//
//    }
//
//    //expect (uut.res, expResult)
//  }

}


//class ComplexRoundingSpec extends FlatSpec with Matchers with DspArithmetic {
//
//  println (s"Running a Functional test for rounding: $roundType")
//
// 
//  // Default backend 
//  val opts = new DspTesterOptionsManager {
//    dspTesterOptions = DspTesterOptions(
//      fixTolLSBs = bitsPrecision,
//      genVerilogTb = false,
//      isVerbose = true
//    )
//  }
//  
//  // Same with Verilator backend
//  val vopts = new DspTesterOptionsManager {
//    dspTesterOptions = DspTesterOptions(
//      fixTolLSBs = bitsPrecision,
//      genVerilogTb = false,
//      isVerbose = true
//    )
//    testerOptions = TesterOptions(
//      backendName = "verilator"
//    )
//  }
//  
//  behavior of "implementation"
//
//  it should "Mul with Stochastic for DspComplex" in {
//    val op  = "mul"
//    
//    dsptools.Driver.execute(() => new ComplexFunctional(cmpInType, cmpOutType, op, pipeDepth, roundType, overflow), opts) { 
//      c => new ComplexFunctionalTester(c, op)
//    } should be(true)
//  }
//  
//}
