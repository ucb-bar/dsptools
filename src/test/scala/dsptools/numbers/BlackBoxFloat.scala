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
}
