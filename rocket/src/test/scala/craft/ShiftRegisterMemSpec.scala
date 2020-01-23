// See LICENSE for license details

package craft

import chisel3._
import dsptools.DspTester
import org.scalatest.{FlatSpec, Matchers}

class SRMemModule( dut: (UInt, Bool) => UInt ) extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(10.W))
    val en = Input(Bool())
    val out = Output(UInt(10.W))
  })

  io.out := dut(io.in, io.en)
}

// expected_output < 0 means don't care
class SRMemTester( dut: SRMemModule, input: Seq[(Int, Boolean)], expected_output: Seq[Int], verbose: Boolean = true)
  extends DspTester(dut) {
  input.zip(expected_output).foreach({case ((num, valid), expected) =>
    poke(dut.io.in, num)
    poke(dut.io.en, valid)
    if (expected >= 0) {
      expect(dut.io.out, expected.U)
    }
    step(1)
  })
}

//noinspection RedundantDefaultArgument,RedundantDefaultArgument,RedundantDefaultArgument,RedundantDefaultArgument
class ShiftRegisterMemSpec extends FlatSpec with Matchers {
  behavior of "ShiftRegisterMem"

  val testVector: Seq[(Int, Boolean)] = Seq(
    1 -> true,
    6 -> false,
    2 -> true,
    3 -> true,
    4 -> true,
    5 -> true,
    0 -> true,
    0 -> true,
    0 -> true,
    0 -> true,
    0 -> true,
    0 -> true,
    0 -> true
  )

  val X = -1

  def runTest (dut : (UInt, Bool) => UInt, expected: Seq[Int]) =
    chisel3.iotesters.Driver(() => new SRMemModule(dut)) {
      c => new SRMemTester(c, testVector, expected)
    } should be (true)

  it should "work with single-ported memories, an enable, and an odd shift" in {
    def testMem(in: UInt, en: Bool): UInt = ShiftRegisterMem(in, 5, en)

    runTest(testMem _,
      Seq(X, X, X, X, X, X, 1, 2, 3, 4, 5, 0, 0)
    )
  }

  it should "work with single-ported memories, an enable, and an even shift" in {
    def testMem(in: UInt, en: Bool): UInt = ShiftRegisterMem(in, 6, en)

    runTest(testMem _,
      Seq(X, X, X, X, X, X, X, 1, 2, 3, 4, 5, 0)
    )
  }

  it should "work with single-ported memories, no enable, and an odd shift" in {
    def testMem(in: UInt, en: Bool) = ShiftRegisterMem(in, 5)

    runTest(testMem _,
      Seq(X, X, X, X, X, 1, 6, 2, 3, 4, 5, 0, 0)
    )
  }

  it should "work with single-ported memories, no enable, and an even shift" in {
    def testMem(in: UInt, en: Bool) = ShiftRegisterMem(in, 6)

    runTest(testMem _,
      Seq(X, X, X, X, X, X, 1, 6, 2, 3, 4, 5, 0)
    )
  }

  it should "work with dual-ported memories, an enable, and an odd shift" in {
    def testMem(in: UInt, en: Bool): UInt = ShiftRegisterMem(in, 5, en, use_sp_mem = false)

    runTest(testMem _,
      Seq(X, X, X, X, X, X, 1, 2, 3, 4, 5, 0, 0)
    )
  }

  it should "work with dual-ported memories, an enable, and an even shift" in {
    def testMem(in: UInt, en: Bool): UInt = ShiftRegisterMem(in, 6, en, use_sp_mem = false)

    runTest(testMem _,
      Seq(X, X, X, X, X, X, X, 1, 2, 3, 4, 5, 0)
    )
  }

  it should "work with dual-ported memories, no enable, and an odd shift" in {
    def testMem(in: UInt, en: Bool) = ShiftRegisterMem(in, 5, use_sp_mem = false)

    runTest(testMem _,
      Seq(X, X, X, X, X, 1, 6, 2, 3, 4, 5, 0, 0)
    )
  }

  it should "work with dual-ported memories, no enable, and an even shift" in {
    def testMem(in: UInt, en: Bool) = ShiftRegisterMem(in, 6, use_sp_mem = false)

    runTest(testMem _,
      Seq(X, X, X, X, X, X, 1, 6, 2, 3, 4, 5, 0)
    )
  }

  it should "work with delay 0" in {
    def testMem(in: UInt, en: Bool) = ShiftRegisterMem(in, 0)

    runTest(testMem _, testVector.map(_._1))

  }

  it should "work with delay 1, and no enable" in {
    def testMem(in: UInt, en: Bool) = ShiftRegisterMem(in, 1)

    runTest(testMem _,
      Seq(X, 1, 6, 2, 3, 4, 5, 0, 0, 0, 0, 0, 0)
    )
  }
  it should "work with delay 1, and an enable" in {
    def testMem(in: UInt, en: Bool) = ShiftRegisterMem(in, 1, en)

    runTest(testMem _,
      Seq(X, 1, 1, 2, 3, 4, 5, 0, 0, 0, 0, 0, 0, 0)
    )
  }
}
