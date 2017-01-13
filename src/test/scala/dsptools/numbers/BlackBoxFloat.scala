//// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import chisel3.util._
import chisel3.testers.BasicTester
import chisel3.iotesters.{ChiselFlatSpec, PeekPokeTester, TesterOptionsManager}
import dsptools.DspTester

//scalastyle:off magic.number

class BlackBoxFloatTester extends BasicTester {
  val (cnt, _) = Counter(true.B, 10)
  val accum = Reg(init=Wire(DspReal(1.0)))

  val addOut = accum + DspReal(1.0)
  val mulOut = addOut * DspReal(2.0)

  accum := addOut

  printf("cnt: %x     accum: %x    add: %x    mult: %x\n",
      cnt, accum.toDoubleBits(), addOut.toDoubleBits(), mulOut.toDoubleBits())

  when (cnt === 0.U) {
    assert(addOut === DspReal(1))
    assert(mulOut === DspReal(2))
  } .elsewhen (cnt === 1.U) {
    assert(addOut === DspReal(2))
    assert(mulOut === DspReal(4))
  } .elsewhen (cnt === 2.U) {
    assert(addOut === DspReal(3))
    assert(mulOut === DspReal(6))
  } .elsewhen (cnt === 3.U) {
    assert(addOut === DspReal(4))
    assert(mulOut === DspReal(8))
  }

  when (cnt >= 3.U) {
    // for unknown reasons, stop needs to be invoked multiple times
    stop()
  }
}

class BlackBoxFloatAdder extends Module {
  val io = IO(new Bundle {
    val a = Input(DspReal(1.0))
    val b = Input(DspReal(1.0))
    val c = Output(DspReal(1.0))
    val d = Output(DspReal(1.0))
    val e = Output(DspReal(1.0))
  })

  io.c := io.a + io.b
  io.d := io.a + io.a
  io.e := io.b + io.b
}

class BlackBoxFloatAdderTester(c: BlackBoxFloatAdder) extends DspTester(c) {
  dspPoke(c.io.a, 2.1)
  dspPoke(c.io.b, 3.0)

  dspExpect(c.io.c, 5.1, "reals should add")
  dspExpect(c.io.d, 4.2, "reals should add")
  dspExpect(c.io.e, 6.0, "reals should add")
}

object FloatOpCodes {
  val Add = 0
  val Subtract = 1
  val Multiply = 2
  val Divide = 3
  val Ln = 4
  val Log10 = 5
  val Exp = 6
  val Sqrt = 7
  val Pow = 8
  val Floor = 9
  val Ceil = 10
  val Sin = 11
  val Cos = 12
  val Tan = 13
  val ASin = 14
  val ACos = 15
  val ATan = 16
  val ATan2 = 17
  val Hypot = 18
  val Sinh = 19
  val Cosh = 20
  val Tanh = 21
  val ASinh = 22
  val ACosh = 23
  val ATanh = 24
}

class FloatOps extends Module {
  import FloatOpCodes._
  val io = IO(new Bundle {
    val in1   = Input(DspReal(1.0))
    val in2   = Input(DspReal(1.0))
    val opsel = Input(UInt(64.W))
    val out   = Output(DspReal(1.0))
  })

  switch (io.opsel) {
    is(Add.U) { io.out := io.in1 + io.in2 }
    is(Subtract.U) { io.out := io.in1 - io.in2 }
    is(Multiply.U) { io.out := io.in1 * io.in2 }
    is(Divide.U) { io.out := io.in1 / io.in2 }
    is(Ln.U) { io.out := io.in1.ln() }
    is(Log10.U) { io.out := io.in1.log10() }
    is(Exp.U) { io.out := io.in1.exp() }
    is(Sqrt.U) { io.out := io.in1.sqrt() }
    is(Pow.U) { io.out := io.in1.pow(io.in2) }
    is(Floor.U) { io.out := io.in1.floor() }
    is(Ceil.U) { io.out := io.in1.ceil() }
    is(Sin.U) { io.out := io.in1.sin() }
    is(Cos.U) { io.out := io.in1.cos() }
    is(Tan.U) { io.out := io.in1.tan() }
    is(ASin.U) { io.out := io.in1.asin() }
    is(ACos.U) { io.out := io.in1.acos() }
    is(ATan.U) { io.out := io.in1.atan() }
    is(ATan2.U) { io.out := io.in1.atan2(io.in2) }
    is(Hypot.U) { io.out := io.in1.hypot(io.in2) }
    is(Sinh.U) { io.out := io.in1.sinh() }
    is(Cosh.U) { io.out := io.in1.cosh() }
    is(Tanh.U) { io.out := io.in1.tanh() }
    is(ASinh.U) { io.out := io.in1.asinh() }
    is(ACosh.U) { io.out := io.in1.acosh() }
    is(ATanh.U) { io.out := io.in1.atanh() }
  }
}

class FloatOpTester(c: FloatOps) extends DspTester(c) {
  import FloatOpCodes._
  val a = 3.4
  val b = 7.1
  // scala doesn't have inverse hyperbolic functions, hardcode them
  val asinh_a = 1.93788
  val acosh_a = 1.8945590126722978042798892652
  dspPoke(c.io.in1, a)
  dspPoke(c.io.in2, b)

  poke(c.io.opsel, Add)
  dspExpect(c.io.out, a + b, "reals should add")
  poke(c.io.opsel, Subtract)
  dspExpect(c.io.out, a - b, "reals should subtract")
  poke(c.io.opsel, Multiply)
  dspExpect(c.io.out, a * b, "reals should multiply")
  poke(c.io.opsel, Divide)
  dspExpect(c.io.out, a / b, "reals should divide")
  poke(c.io.opsel, Ln)
  dspExpect(c.io.out, math.log(a), "log should work on reals")
  poke(c.io.opsel, Log10)
  dspExpect(c.io.out, math.log10(a), "log10 should work on reals")
  poke(c.io.opsel, Exp)
  dspExpect(c.io.out, math.exp(a), "exp should work on reals")
  poke(c.io.opsel, Sqrt)
  dspExpect(c.io.out, math.sqrt(a), "sqrt should work on reals")
  poke(c.io.opsel, Pow)
  dspExpect(c.io.out, math.pow(a, b), "reals should pow")
  poke(c.io.opsel, Floor)
  dspExpect(c.io.out, math.floor(a), "floor should work on reals")
  poke(c.io.opsel, Ceil)
  dspExpect(c.io.out, math.ceil(a), "ceil should work on reals")
  poke(c.io.opsel, Sin)
  dspExpect(c.io.out, math.sin(a), "sin should work on reals")
  poke(c.io.opsel, Cos)
  dspExpect(c.io.out, math.cos(a), "cos should work on reals")
  poke(c.io.opsel, Tan)
  dspExpect(c.io.out, math.tan(a), "tan should work on reals")
  poke(c.io.opsel, ASin)
  dspExpect(c.io.out, math.asin(a), "asin should work on reals")
  poke(c.io.opsel, ACos)
  dspExpect(c.io.out, math.acos(a), "acos should work on reals")
  poke(c.io.opsel, ATan)
  dspExpect(c.io.out, math.atan(a), "atan should work on reals")
  poke(c.io.opsel, ATan2)
  poke(c.io.opsel, Hypot)
  poke(c.io.opsel, Sinh)
  dspExpect(c.io.out, math.sinh(a), "sinh should work on reals")
  poke(c.io.opsel, Cosh)
  dspExpect(c.io.out, math.cosh(a), "cosh should work on reals")
  poke(c.io.opsel, Tanh)
  dspExpect(c.io.out, math.tanh(a), "tanh should work on reals")
  poke(c.io.opsel, ASinh)
  dspExpect(c.io.out, asinh_a, "asinh should work on reals")
  poke(c.io.opsel, ACosh)
  dspExpect(c.io.out, acosh_a, "acosh should work on reals")
  poke(c.io.opsel, ATanh)
  // not defined
  // dspExpect(c.io.out, math.atanh(a), "atanh should work on reals")
}

class BlackBoxFloatSpec extends ChiselFlatSpec {
  "A BlackBoxed FP block" should "work" in {
    assertTesterPasses({ new BlackBoxFloatTester },
        Seq("/BlackBoxFloat.v"))
  }

  "basic addition" should "work with reals through black boxes" in {
    val optionsManager = new TesterOptionsManager {
      interpreterOptions = interpreterOptions.copy(
        blackBoxFactories = interpreterOptions.blackBoxFactories :+ new DspRealFactory)
//        testerOptions = testerOptions.copy(backendName = "verilator")
    }

    dsptools.Driver.execute(() => new BlackBoxFloatAdder, optionsManager) { c =>
      new BlackBoxFloatAdderTester(c)
    } should be(true)
  }

  "float ops" should "work with interpreter" in {
    val optionsManager = new TesterOptionsManager {
      interpreterOptions = interpreterOptions.copy(
        blackBoxFactories = interpreterOptions.blackBoxFactories :+ new DspRealFactory)
    }

    dsptools.Driver.execute(() => new FloatOps, optionsManager) { c =>
      new FloatOpTester(c)
    } should be(true)
  }

  "float ops" should "work with verilator" in {
    val optionsManager = new TesterOptionsManager {
        testerOptions = testerOptions.copy(backendName = "verilator")
    }

    dsptools.Driver.execute(() => new FloatOps, optionsManager) { c =>
      new FloatOpTester(c)
    } should be(true)
  }
}
