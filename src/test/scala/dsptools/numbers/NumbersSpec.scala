// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import chisel3.util._
import dsptools._
import chisel3.experimental.FixedPoint
import dsptools.numbers.implicits._
import org.scalatest.{FreeSpec, Matchers}

/**
  * This will attempt to follow the dsptools.numbers.README.md file as close as possible.
  */
//scalastyle:off magic.number
class NumbersSpec extends FreeSpec with Matchers {
  def f(w: Int, b: Int): FixedPoint = FixedPoint(w.W, b.BP)
  def u(w: Int): UInt = UInt(w.W)
  def s(w: Int): SInt = SInt(w.W)

  "dsptools provides extensive tools for implementing well behaved mathematical operations" - {
    "the behavior of operators and methods can be controlled with the DspContext object" - {
      "overflow type controls how a * b, a.trimBinary(n) and a.div2(n) should round results" - {
        "Overflow type tests for UInt" in {
          dsptools.Driver.execute(
            () => new OverflowTypeCircuit(u(4), u(5), u(5)),
            Array("--backend-name", "firrtl")
          ) { c =>
            new OverflowTypeCircuitTester(c,
              // in1, in2, addWrap, addGrow, subWrap, subGrow
              (15, 1, 0, 16, 14, 0),
              (14, 2, 0, 16, 12, 0),
              (1, 2, 3, 3, 15, 0)
            )
          } should be(true)
          dsptools.Driver.execute(
            () => new OverflowTypeCircuit(u(4), u(5), u(5)),
            Array("--backend-name", "verilator")
          ) { c =>
            new OverflowTypeCircuitTester(c,
              // in1, in2, addWrap, addGrow, subWrap, subGrow
              (15, 1, 0, 16, 14, 0),
              (14, 2, 0, 16, 12, 0),
              (1, 2, 3, 3, 15, 0)
            )
          } should be(true)
        }
        "UInt subtract with overflow type Grow not supported" in {
          intercept[DspException] {
            dsptools.Driver.execute(() => new BadUIntSubtractWithGrow2(u(4))) { c =>
              new NumbersEmptyTester(c)
            } should be(true)
          }
        }
        "UInt subtract with overflow type Grow not supported cannot be detected without evidence that io is ring" in {
          dsptools.Driver.execute(() => new ShouldBeBadUIntSubtractWithGrow) { c =>
            new NumbersEmptyTester(c)
          } should be(true)
        }
        "Overflow type tests for SInt" in {
          dsptools.Driver.execute(
            () => new OverflowTypeCircuit(s(4), s(5), s(5)),
            Array("--backend-name", "firrtl")
          ) { c =>
            new OverflowTypeCircuitTester(c,
              // in1, in2, addWrap, addGrow, subWrap, subGrow
              (7, 2, -7, 9, 5, 5),
              (-8, 2, -6, -6, 6, -10),
              (-8, -2, 6, -10, -6, -6)
            )

          } should be(true)
          dsptools.Driver.execute(
            () => new OverflowTypeCircuit(s(4), s(5), s(5)),
            Array("--backend-name", "verilator")
          ) { c =>
            new OverflowTypeCircuitTester(c,
              // in1, in2, addWrap, addGrow, subWrap, subGrow
              (7, 2, -7, 9, 5, 5),
              (-8, 2, -6, -6, 6, -10),
              (-8, -2, 6, -10, -6, -6)
            )

          } should be(true)
        }
        "Overflow type tests for FixedPoint" in {
          dsptools.Driver.execute(
            () => new OverflowTypeCircuit(f(4, 2), f(5, 2), f(8, 3)),
            Array("--backend-name", "firrtl")
          ) { c =>
            new OverflowTypeCircuitTester(c,
              // in1, in2, addWrap, addGrow, subWrap, subGrow
              (1.75, 0.5, -1.75, 2.25, 1.25, 1.25),
              (-1.75, 0.5, -1.25, -1.25, 1.75, -2.25),
              (-1.75, -0.5, 1.75, -2.25, -1.25, -1.25)
            )

          } should be(true)
          dsptools.Driver.execute(
            () => new OverflowTypeCircuit(f(4, 2), f(5, 2), f(8, 3)),
            Array("--backend-name", "verilator")
          ) { c =>
            new OverflowTypeCircuitTester(c,
              // in1, in2, addWrap, addGrow, subWrap, subGrow
              (1.75, 0.5, -1.75, 2.25, 1.25, 1.25),
              (-1.75, 0.5, -1.25, -1.25, 1.75, -2.25),
              (-1.75, -0.5, 1.75, -2.25, -1.25, -1.25)
            )

          } should be(true)
        }
      }
    }
      "trimType controls how a * b, a.trimBinary(n) and a.div2(n) should round results" - {
        "TrimType tests" in {
          def f(w: Int, b: Int): FixedPoint = FixedPoint(w.W, b.BP)
          dsptools.Driver.execute(
            //            () => new TrimTypeCircuit(f(4, 2), f(10, 5), f(10, 5))) { c =>
            () => new TrimTypeCircuit(f(4, 2), f(10, 5), f(20, 7)),
            Array("--backend-name", "verilator")
          ) { c =>
            new TrimTypeCircuitTester(c)
          } should be(true)
        }
      }
  }
}

//scalastyle:off regex
class TrimTypeCircuitTester[T <: Data : Ring](c: TrimTypeCircuit[T]) extends DspTester(c) {
  poke(c.io.a, 1.25)
  poke(c.io.b, 1.25)
  step(2)
  println(s"peek ${peek(c.io.multiplyRoundHalfUp)}")
  println(s"peek ${peek(c.io.multiplyNoTrim)}")
  println(s"1.25 * 1.25 = ${1.25 * 1.25}")
}

class TrimTypeCircuit[T <: Data : Ring](gen1: T, gen2: T, gen3: T) extends Module {
  val io = IO(new Bundle {
    val a = Input(gen1)
    val b = Input(gen1)
    val multiplyRoundHalfUp = Output(gen2)
    val multiplyNoTrim = Output(gen3)
  })

  val regMultiplyRoundHalfUp = RegNext(DspContext.withTrimType(RoundHalfUp) {
    io.a * io.b
  })
  val regMultiplyNoTrim = RegNext(DspContext.withTrimType(NoTrim) {
    io.a * io.b
  })

  io.multiplyRoundHalfUp := regMultiplyRoundHalfUp
  io.multiplyNoTrim := regMultiplyNoTrim
}

class OverflowTypeCircuitTester[T <: Data : Ring, U <: Data : Ring]
(
  c: OverflowTypeCircuit[T, U],
  testVectors: (Double, Double, Double, Double, Double, Double)*
) extends DspTester(c) {
  for((a, b, e1, e2, e3, e4) <- testVectors) {
    poke(c.io.a, a)
    poke(c.io.b, b)

    step(1)

    expect(c.io.addWrap, e1)
    expect(c.io.addGrow, e2)
    expect(c.io.subWrap, e3)
    expect(c.io.subGrow, e4)
  }
}

class OverflowTypeCircuit[T <: Data : Ring, U <: Data : Ring]
(gen1: T, gen2: T, gen3: U) extends Module {
  val io = IO(new Bundle {
    val a = Input(gen1)
    val b = Input(gen1)
    val addWrap = Output(gen2)
    val addGrow = Output(gen2)
    val subWrap = Output(gen3)
    val subGrow = Output(gen3)
  })

  val regAddWrap = RegNext(DspContext.withOverflowType(Wrap) { io.a + io.b })
  val regAddGrow = RegNext(DspContext.withOverflowType(Grow) { io.a + io.b })

  val regSubWrap = RegNext(DspContext.withOverflowType(Wrap) { io.a - io.b })
  val regSubGrow = RegNext(if (io.a.isInstanceOf[UInt]) 0.U else DspContext.withOverflowType(Grow) { io.a - io.b })
//  val regSubGrow = RegNext(DspContext.withOverflowType(Grow) { io.a - io.b })

  io.addWrap := regAddWrap
  io.addGrow := regAddGrow
  io.subWrap := regSubWrap
  io.subGrow := regSubGrow
}

class NumbersEmptyTester[T <: Module](c: T) extends DspTester(c)

class ShouldBeBadUIntSubtractWithGrow extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(4.W))
    val b = Input(UInt(4.W))
    val o = Output(UInt(4.W))
  })
  val r = RegNext(DspContext.withOverflowType(Grow) { io.a - io.b })
  io.o := r
}

class BadUIntSubtractWithGrow2[T <: Data : Ring](gen: T) extends Module {
  val io = IO(new Bundle {
    val a = Input(gen)
    val b = Input(gen)
    val o = Output(gen)
  })
  val r = RegNext(DspContext.withOverflowType(Grow) { io.a - io.b })
  io.o := r
}
