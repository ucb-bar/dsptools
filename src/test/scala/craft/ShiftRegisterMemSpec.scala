// See LICENSE for license details

package pfb

import chisel3._
import dsptools.DspTester
import org.scalatest.{FlatSpec, Matchers}

class SRMemModule( dut: (UInt, Bool) => UInt ) extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt.width(10))
    val en = Input(Bool())
    val out = Output(UInt.width(10))
  })

  io.out := dut(io.in, io.en)
}

// expected_output < 0 means don't care
class SRMemTester( dut: SRMemModule, input: Seq[(Int, Boolean)], expected_output: Seq[Int], verbose: Boolean = true)
  extends DspTester(dut) {
  input.zip(expected_output).foreach({case ((num, valid), expected) => {
    poke(dut.io.in, num)
    poke(dut.io.en, valid)
    if (expected >= 0) {
      expect(dut.io.out, UInt(expected))
    }
    step(1)
  }})
}

class ShiftRegisterMemSpec extends FlatSpec with Matchers {
  behavior of "ShiftRegisterMem"

  val testVector: Seq[(Int, Boolean)] = Seq(
    1 -> true,
    1 -> false,
    2 -> true,
    3 -> true,
    4 -> true,
    5 -> true,
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

  it should "work with no init and an enable signal" in {
    def testMem(in: UInt, en: Bool): UInt = ShiftRegisterMem(5, in, en)

    runTest(testMem _,
      Seq(X, X, X, X, X, X, 1, 2, 3, 4, 5, 0)
    )

  }

  it should "work with no init and no enable signal" in {
    def testMem(in: UInt, en: Bool) = ShiftRegisterMem(5, in)

    runTest(testMem _,
      Seq(X, X, X, X, X, 1, 1, 2, 3, 4, 5, 0)
    )
  }

  //it should "work with an init and an enable signal" in {
  //  def testMem(in: UInt, en: Bool) = ShiftRegisterMem(5, in, en, Some(UInt(1)))

  //  runTest(testMem _,
  //    Seq(1, X, 1, 1, 1, 1, 1, 2, 3, 4, 5, 0)
  //  )
  //}

  //it should "work with an init and no enable signal" in {
  //  def testMem(in: UInt, en: Bool) = ShiftRegisterMem(5, in, init = Some(UInt(1)))


  //  runTest(testMem _,
  //    Seq(1, X, 1, 1, 1, 1, 1, 2, 3, 4, 5, 0)
  //  )
  //}

  it should "work with delay 0" in {
    def testMem(in: UInt, en: Bool) = ShiftRegisterMem(0, in)

    runTest(testMem _, testVector.map(_._1))

  }

  it should "work with delay 1" in {
    def testMem(in: UInt, en: Bool) = ShiftRegisterMem(1, in)


    runTest(testMem _,
      Seq(X, 1, 1, 2, 3, 4, 5, 0, 0, 0, 0, 0)
    )
  }
}
