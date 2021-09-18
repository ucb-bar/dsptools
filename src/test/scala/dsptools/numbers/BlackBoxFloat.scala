// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3._
import chisel3.iotesters.{ChiselFlatSpec, PeekPokeTester, TesterOptionsManager}
import chisel3.testers.BasicTester
import chisel3.util._
import dsptools.DspTester

//scalastyle:off magic.number regex

class BlackBoxFloatTester extends BasicTester {
  val (cnt, _) = Counter(true.B, 10)
  val accum = RegInit(DspReal(0))

  private val addOut = accum + DspReal(1.0)
  private val mulOut = addOut * DspReal(2.0)

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
    val a = Input(DspReal())
    val b = Input(DspReal())
    val c = Output(DspReal())
    val d = Output(DspReal())
    val e = Output(DspReal())
  })

  io.c := io.a + io.b
  io.d := io.a + io.a
  io.e := io.b + io.b
}

class BlackBoxFloatAdderTester(c: BlackBoxFloatAdder) extends DspTester(c) {
  poke(c.io.a, 2.1)
  poke(c.io.b, 3.0)

  expect(c.io.c, 5.1, "reals should add")
  expect(c.io.d, 4.2, "reals should add")
  expect(c.io.e, 6.0, "reals should add")
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
  val GreaterThan = 25
  val GreaterThanOrEqual = 26
  val LessThan = 27
  val LessThanOrEqual = 28
}

class FloatOps extends Module {
  val io = IO(new Bundle {
    val in1 = Input(DspReal())
    val in2 = Input(DspReal())
    val opsel = Input(UInt(64.W))
    val out = Output(DspReal())
    val boolOut = Output(Bool())
  })

  io.boolOut := false.B
  io.out := DspReal(0)
}

class FloatOpsWithTrig extends FloatOps {
  import FloatOpCodes._

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
    is(FloatOpCodes.Floor.U) { io.out := io.in1.floor() }
    is(Ceil.U) { io.out := io.in1.ceil() }

    is(GreaterThan.U) { io.boolOut := io.in1 > io.in2 }
    is(GreaterThanOrEqual.U) { io.boolOut := io.in1 >= io.in2 }

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

class FloatOpsWithoutTrig extends FloatOps {
  import FloatOpCodes._
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
    is(FloatOpCodes.Floor.U) { io.out := io.in1.floor() }
    is(Ceil.U) { io.out := io.in1.ceil() }
    is(GreaterThan.U) { io.boolOut := io.in1 > io.in2 }
    is(GreaterThanOrEqual.U) { io.boolOut := io.in1 >= io.in2 }
  }
}

class FloatOpTester[T <: FloatOps](c: T, testTrigFuncs: Boolean = true) extends DspTester(c) {
  import FloatOpCodes._
  val a = 3.4
  val b = 7.1
  // scala doesn't have inverse hyperbolic functions, hardcode them
  val asinh_a = 1.9378792776645006
  val acosh_a = 1.8945590126722978042798892652
  poke(c.io.in1, a)
  poke(c.io.in2, b)

  poke(c.io.opsel, Add)
  expect(c.io.out, a + b, "reals should add")
  poke(c.io.opsel, Subtract)
  expect(c.io.out, a - b, "reals should subtract")
  poke(c.io.opsel, Multiply)
  expect(c.io.out, a * b, "reals should multiply")
  poke(c.io.opsel, Divide)
  expect(c.io.out, a / b, "reals should divide")
  poke(c.io.opsel, Ln)
  expect(c.io.out, math.log(a), "log should work on reals")
  poke(c.io.opsel, Log10)
  expect(c.io.out, math.log10(a), "log10 should work on reals")
  poke(c.io.opsel, Exp)
  expect(c.io.out, math.exp(a), "exp should work on reals")
  poke(c.io.opsel, Sqrt)
  expect(c.io.out, math.sqrt(a), "sqrt should work on reals")
  poke(c.io.opsel, Pow)
  expect(c.io.out, math.pow(a, b), "reals should pow")
  poke(c.io.opsel, FloatOpCodes.Floor)
  expect(c.io.out, math.floor(a), "floor should work on reals")
  poke(c.io.opsel, Ceil)
  expect(c.io.out, math.ceil(a), "ceil should work on reals")

  if(testTrigFuncs) {
    poke(c.io.opsel, Sin)
    expect(c.io.out, math.sin(a), "sin should work on reals")
    poke(c.io.opsel, Cos)
    expect(c.io.out, math.cos(a), "cos should work on reals")
    poke(c.io.opsel, Tan)
    expect(c.io.out, math.tan(a), "tan should work on reals")

    val arcArg = 0.5
    poke(c.io.in1, arcArg)
    poke(c.io.opsel, ASin)
    expect(c.io.out, math.asin(arcArg), "asin should work on reals")
    poke(c.io.opsel, ACos)
    expect(c.io.out, math.acos(arcArg), "acos should work on reals")

    poke(c.io.in1, a)
    poke(c.io.opsel, ATan)
    expect(c.io.out, math.atan(a), "atan should work on reals")
    poke(c.io.opsel, ATan2)
    expect(c.io.out, math.atan2(a, b), "atan2 should work on reals")
    poke(c.io.opsel, Hypot)
    expect(c.io.out, math.hypot(a, b), "hypot should work on reals")
    poke(c.io.opsel, Sinh)
    expect(c.io.out, math.sinh(a), "sinh should work on reals")
    poke(c.io.opsel, Cosh)
    expect(c.io.out, math.cosh(a), "cosh should work on reals")
    poke(c.io.opsel, Tanh)
    expect(c.io.out, math.tanh(a), "tanh should work on reals")
    poke(c.io.opsel, ASinh)
    expect(c.io.out, asinh_a, "asinh should work on reals")
    poke(c.io.opsel, ACosh)
    expect(c.io.out, acosh_a, "acosh should work on reals")
    poke(c.io.opsel, ATanh)
    // not defined
    // dspExpect(c.io.out, math.atanh(a), "atanh should work on reals")
  }

  for {
    x <- (BigDecimal(-1.0) to 1.0 by 1.0).map(_.toDouble)
    y <- (BigDecimal(-1.0) to 1.0 by 1.0).map(_.toDouble)
  } {
    poke(c.io.in1, x)
    poke(c.io.in2, y)
    poke(c.io.opsel, GreaterThan)
    expect(c.io.boolOut, x > y, s"$x > $y should be ${x > y}")
    step(1)
  }

}

class BlackBoxFloatSpec extends ChiselFlatSpec {
  "A BlackBoxed FP block" should "work" in {
    assertTesterPasses({ new BlackBoxFloatTester }
      , Seq(
      "/BBFAdd.v",
      "/BBFMultiply.v",
      "/BBFEquals.v"
      )
    )
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

    dsptools.Driver.execute(() => new FloatOpsWithTrig, optionsManager) { c =>
      new FloatOpTester(c)
    } should be(true)
  }

  "float ops" should "work with verilator" in {
    val optionsManager = new TesterOptionsManager {
        testerOptions = testerOptions.copy(backendName = "verilator")
    }

    dsptools.Driver.execute(() => new FloatOpsWithoutTrig, optionsManager) { c =>
      new FloatOpTester(c, testTrigFuncs = false)
    } should be(true)
  }

  "greater than" should "work with negatives" in {
    val optionsManager = new TesterOptionsManager {
      interpreterOptions = interpreterOptions.copy(
        blackBoxFactories = interpreterOptions.blackBoxFactories :+ new DspRealFactory)
    }

    dsptools.Driver.execute(() => new NegCircuit, optionsManager) { c =>
      new NegCircuitTester(c)
    } should be(true)
  }
}

class NegCircuit extends Module {
  val io = IO(new Bundle {
    val in1 = Input(DspReal())
    val in2 = Input(DspReal())
    val out = Output(Bool())
  })
  io.out := io.in1 > io.in2
}

class NegCircuitTester(c: NegCircuit) extends DspTester(c) {
  poke(c.io.in1, -1.0)
  poke(c.io.in2, -2.0)
  expect(c.io.out, true)
}
