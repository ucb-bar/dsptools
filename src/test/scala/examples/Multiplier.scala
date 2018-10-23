package demo_pkg

import chisel3._

import dsptools.numbers.{Ring, DspComplex}
import dsptools.numbers.implicits._

import chisel3.core.{FixedPoint => FP}
import dsptools.{DspTester, DspTesterOptions, DspTesterOptionsManager, DspContext, TrimType}
import dsptools.{NoTrim, RoundHalfUp, StochasticRound}

import org.scalatest.{Matchers, FlatSpec, FunSpec}
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

  val a  = 5.29
  val b  = 2.33
  

  //(1 to 2) foreach { i =>

    poke(uut.a, a)
    poke(uut.b, b)
    step (3)
    expect (uut.res, a*b)
  //}
}

//class ComplexMultiplierTester[A <: Data:Ring, B <: Data:Ring](c: Multiplier[A,B]) extends DspTester(c) {
//  val uut  = c.io
//  
//  for {
//    i <- 0.0 to 1.0 by 0.25
//    j <- 0.0 to 4.0 by 0.5
//  } {
//    val expected = i * j
//
//    poke(uut.a.real, i)
//    poke(uut.a.imag, i+1)
//    poke(uut.b.real, j)
//    poke(uut.b.imag, j+2)
//    step(1)
//
//  }
//
//}

class MultiplierSpec extends FlatSpec with Matchers  {
//class MultiplierSpec extends FlatSpec with PropertyChecks  {

  val inType  = FP(16.W, 8.BP)
  val outType = FP(20.W, 12.BP)

  val complexInType   = DspComplex(inType.cloneType, inType.cloneType)
  val complexOutType  = DspComplex(outType.cloneType, outType.cloneType)

  val opts = new DspTesterOptionsManager {
  
    dspTesterOptions = DspTesterOptions(
      fixTolLSBs = 4,
      genVerilogTb = false,
      isVerbose = true
    )
  }
  
  behavior of "implementation"

  it should "Rnd Half UP for FixedPoint" in {
    //dsptools.Driver.execute (() => new Multiplier(inType, outType, 2), Array("--backend-name", "verilator")) {
    dsptools.Driver.execute(() => new Multiplier(inType, outType, 2, RoundHalfUp), opts) { 
      c => new MultiplierTester(c)
    } should be(true)
  }
  
  //it should "Rnd Stochastic for FixedPoint" in {
  //  //dsptools.Driver.execute (() => new Multiplier(inType, outType, 2), Array("--backend-name", "verilator")) {
  //  dsptools.Driver.execute(() => new Multiplier(inType, outType, 2, StochasticRound), opts) { 
  //    c => new MultiplierTester(c)
  //  } should be(true)
  //}
  
  //it should "Rnd Stochastic for Complex" in {
  //  //dsptools.Driver.execute (() => new Multiplier(inType, outType, 2), Array("--backend-name", "verilator")) {
  //  dsptools.Driver.execute(() => new Multiplier(complexInType, complexOutType, 2, StochasticRound), opts) { 
  //    c => new MultiplierTester(c)
  //  } should be(true)
  //}

}

class MultiplierPropSpec extends FunSpec with PropertyChecks  { 
  
  val opts = new DspTesterOptionsManager {
  
    dspTesterOptions = DspTesterOptions(
      fixTolLSBs = 4,
      genVerilogTb = false,
      isVerbose = true
    )
  }

  val inType  = FP(16.W, 8.BP)
  val outType = FP(32.W, 20.BP)
    
  def add[A](x:A, y:A)(implicit num:Numeric[A]):A = num.plus(x,y)
  def mul[A](x:A, y:A)(implicit num:Numeric[A]):A = num.times(x,y)
  
  def sub[A](x:A, y:A)(implicit num:Numeric[A]):A = {
    val negY  = num.negate(y)
    num.plus(x,negY)
  }
  def fma[A](x:A, y:A, c:A)(implicit num:Numeric[A]):A = {
    val mulXY = num.times(x,y)
    num.plus(mulXY, c)
  }

  describe ("Stochastic Round Checker") {


    //it ("Add for Int") {
    //  forAll {
    //    (a:Int, b:Int) => assert (add(a,b) == a+b)
    //  }
    //}
    //
    //it ("Sub for Int") {
    //  forAll {
    //    (a:Int, b:Int) => assert (sub(a,b) == a-b)
    //  }
    //}
    //
    //it ("Mul for Double") {
    //  forAll {
    //    (a:Double, b:Double) => assert (mul(a,b) == a*b)
    //  }
    //}
    //
    it ("Fma for Double") {
      forAll {
        (a:Double, b:Double, c:Double) => assert (fma(a,b,c) == a*b + c)
      }
    }
    
  // FIXME  How to pass input data from here to the Peek poke tester ?    
  it ("Mul DSP for Double") {
    forAll {
      (a:Double, b:Double) => assert ( mul(a,b) == 
  
      dsptools.Driver.execute(() => new Multiplier(inType, outType, 2, RoundHalfUp), opts) {
        c => new MultiplierTester(c)
      }
        
      )
    }
  }

  }
}
