//// See LICENSE for license details.

package dsptools.numbers

import Chisel._
import Chisel.testers.BasicTester
import chisel3.iotesters.ChiselFlatSpec

//scalastyle:off magic.number

class BlackBoxFloatTester extends BasicTester {
  val (cnt, _) = Counter(Bool(true), 10)
  val accum = Reg(init=Wire(DspReal(1.0)))

  val addOut = accum + DspReal(1.0)
  val mulOut = addOut * DspReal(2.0)

  accum := addOut

  printf("cnt: %x     accum: %x    add: %x    mult: %x\n",
      cnt, accum.toDoubleBits(), addOut.toDoubleBits(), mulOut.toDoubleBits())

  when (cnt === UInt(0)) {
    assert(addOut === DspReal(1))
    assert(mulOut === DspReal(2))
  } .elsewhen (cnt === UInt(1)) {
    assert(addOut === DspReal(2))
    assert(mulOut === DspReal(4))
  } .elsewhen (cnt === UInt(2)) {
    assert(addOut === DspReal(3))
    assert(mulOut === DspReal(6))
  } .elsewhen (cnt === UInt(3)) {
    assert(addOut === DspReal(4))
    assert(mulOut === DspReal(8))
  }

  when (cnt >= UInt(3)) {
    // for unknown reasons, stop needs to be invoked multiple times
    stop()
  }
}

class BlackBoxFloatSpec extends ChiselFlatSpec {
  "A BlackBoxed FP block" should "work" in {
    assertTesterPasses({ new BlackBoxFloatTester },
        Seq("/BlackBoxFloat.v"))
  }
}
