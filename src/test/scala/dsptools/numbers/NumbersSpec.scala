// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3._
import dsptools._
import chisel3.experimental.FixedPoint
import dsptools.numbers.implicits._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
  * This will attempt to follow the dsptools.numbers.README.md file as close as possible.
  */
//scalastyle:off magic.number
class NumbersSpec extends AnyFreeSpec with Matchers {
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
        "UInt subtract with overflow type Grow not supported" ignore {
          val expectedMessage = "OverflowType Grow is not supported for UInt subtraction"
          val exception = intercept[Exception] {
            dsptools.Driver.execute(() => new BadUIntSubtractWithGrow2(u(4))) { c =>
              new NumbersEmptyTester(c)
            }
          }
          exception match {
            case e: DspException => e.getMessage should be(expectedMessage)
            case e: Exception => exception.getCause should be(new DspException(expectedMessage))
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
      "trim type controls how a * b, a.trimBinary(n) and a.div2(n) should round results" - {
        "Trim type tests for multiplication" in {
          dsptools.Driver.execute(
            () => new TrimTypeMultiplyCircuit(f(6, 2), f(8, 4), f(12, 5)),
            Array("--backend-name", "firrtl")
          ) { c =>
            new TrimTypeMultiplyCircuitTester(c,
              // a, b, mulF, mulC, mulRTZ, mulRTI, mulRHD, mulRHUp, mulRHTZ, mulRHTI, mulRHTE, mulRHTO, mulNoTrim
              (1.5, 1.25, 1.75, 2.0, 1.75, 2.0, 1.75, 2.0, 1.75, 2.0, 2.0, 1.75, 1.875),
              (1.25, 1.25, 1.5, 1.75, 1.5, 1.75, 1.5, 1.5, 1.5, 1.5, 1.5, 1.5, 1.5625),
              (0.75, 1.5, 1.0, 1.25, 1.0, 1.25, 1.0, 1.25, 1.0, 1.25, 1.0, 1.25, 1.125),
              (1.25, 0.75, 0.75, 1.0, 0.75, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.9375),
              (-1.5, 1.25, -2.0, -1.75, -1.75, -2.0, -2.0, -1.75, -1.75, -2.0, -2.0, -1.75, -1.875),
              (-1.25, 1.25, -1.75, -1.5, -1.5, -1.75, -1.5, -1.5, -1.5, -1.5, -1.5, -1.5, -1.5625),
              (-0.75, 1.5, -1.25, -1.0, -1.0, -1.25, -1.25, -1.0, -1.0, -1.25, -1.0, -1.25, -1.125),
              (-1.25, 0.75, -1.0, -0.75, -0.75, -1.0, -1.0, -1.0, -1.0, -1.0, -1.0, -1.0, -0.9375)
            )
          } should be(true)
        }
      "Trim type tests for division" in {
          dsptools.Driver.execute(
            () => new TrimTypeDiv2Circuit(f(6, 2), f(8, 4), f(12, 5)),
            Array("--backend-name", "firrtl")
          ) { c =>
            new TrimTypeDiv2CircuitTester(c,
              // a, divF, divC, divRTZ, divRTI, divRHD, divRHUp, divRHTZ, divRHTI, divRHTE, divRHTO, divNoTrim
              (1.5, 0.25, 0.5, 0.25, 0.5, 0.25, 0.5, 0.25, 0.5, 0.5, 0.25, 0.375),
              (1.25, 0.25, 0.5, 0.25, 0.5, 0.25, 0.25, 0.25, 0.25, 0.25, 0.25, 0.3125),
              (0.75, 0.0, 0.25, 0.0, 0.25, 0.25, 0.25, 0.25, 0.25, 0.25, 0.25, 0.1875),
              (0.5, 0.0, 0.25, 0.0, 0.25, 0.0, 0.25, 0.0, 0.25, 0.0, 0.25, 0.125),
              (-1.5, -0.5, -0.25, -0.25, -0.5, -0.5, -0.25, -0.25, -0.5, -0.5, -0.25, -0.375),
              (-1.25, -0.5, -0.25, -0.25, -0.5, -0.25, -0.25, -0.25, -0.25, -0.25, -0.25, -0.3125),
              (-0.75, -0.25, -0.0, -0.0, -0.25, -0.25, -0.25, -0.25, -0.25, -0.25, -0.25, -0.1875),
              (-0.5, -0.25, -0.0, -0.0, -0.25, -0.25, -0.0, -0.0, -0.25, -0.0, -0.25, -0.125)
            )
          } should be(true)
        }
      }
    }
    "Test for BinaryRepresentation section of Numbers Spec" in {
      def f(w: Int, b: Int): FixedPoint = FixedPoint(w.W, b.BP)
      dsptools.Driver.execute(
        () => new BinaryRepr(u(8), s(8), f(10, 2)),
        Array("--backend-name", "verilator")
      ) { c =>
        new BinaryReprTester(c)
      } should be(true)
    }
  }
}

class TrimTypeMultiplyCircuit[T <: Data : Ring](gen1: T, gen2: T, gen3: T) extends Module{
  val io = IO(new Bundle {
    val a = Input(gen1)
    val b = Input(gen1)
    val multiplyFloor = Output(gen2)
    val multiplyCeiling = Output(gen2)
    val multiplyRoundTowardsZero = Output(gen2)
    val multiplyRoundTowardsInfinity = Output(gen2)
    val multiplyRoundHalfDown = Output(gen2)
    val multiplyRoundHalfUp = Output(gen2)
    val multiplyRoundHalfTowardsZero = Output(gen2)
    val multiplyRound = Output(gen2)
    val multiplyConvergent = Output(gen2)
    val multiplyRoundHalfToOdd = Output(gen2)
    val multiplyNoTrim = Output(gen3)
  })

  DspContext.withBinaryPointGrowth(0){
    val regMultiplyFloor = RegNext(DspContext.withTrimType(Floor) {
      io.a context_* io.b
    })
    val regMultiplyCeiling = RegNext(DspContext.withTrimType(Ceiling) {
      io.a context_* io.b
    })
    val regMultiplyRoundTowardsZero = RegNext(DspContext.withTrimType(RoundTowardsZero) {
      io.a context_* io.b
    })
    val regMultiplyRoundTowardsInfinity = RegNext(DspContext.withTrimType(RoundTowardsInfinity) {
      io.a context_* io.b
    })
    val regMultiplyRoundHalfDown = RegNext(DspContext.withTrimType(RoundHalfDown) {
      io.a context_* io.b
    })
     val regMultiplyRoundHalfUp = RegNext(DspContext.withTrimType(RoundHalfUp) {
      io.a context_* io.b
    })
    val regMultiplyRoundHalfTowardsZero = RegNext(DspContext.withTrimType(RoundHalfTowardsZero) {
      io.a context_* io.b
    })
    val regMultiplyRound = RegNext(DspContext.withTrimType(Round) {
      io.a context_* io.b
    })
    val regMultiplyConvergent = RegNext(DspContext.withTrimType(Convergent) {
      io.a context_* io.b
    })
    val regMultiplyRoundHalfToOdd = RegNext(DspContext.withTrimType(RoundHalfToOdd) {
      io.a context_* io.b
    })
    val regMultiplyNoTrim = RegNext(DspContext.withTrimType(NoTrim) {
      io.a context_* io.b
    })

    io.multiplyFloor := regMultiplyFloor
    io.multiplyCeiling := regMultiplyCeiling
    io.multiplyRoundTowardsZero := regMultiplyRoundTowardsZero
    io.multiplyRoundTowardsInfinity := regMultiplyRoundTowardsInfinity
    io.multiplyRoundHalfDown := regMultiplyRoundHalfDown
    io.multiplyRoundHalfUp := regMultiplyRoundHalfUp
    io.multiplyRoundHalfTowardsZero := regMultiplyRoundHalfTowardsZero
    io.multiplyRound := regMultiplyRound
    io.multiplyConvergent := regMultiplyConvergent
    io.multiplyRoundHalfToOdd := regMultiplyRoundHalfToOdd
    io.multiplyNoTrim := regMultiplyNoTrim
  }
}

class TrimTypeDiv2Circuit[T <: Data : Ring : BinaryRepresentation](gen1: T, gen2: T, gen3: T) extends Module{
  val io = IO(new Bundle {
    val a = Input(gen1)
    val div2Floor = Output(gen2)
    val div2Ceiling = Output(gen2)
    val div2RoundTowardsZero = Output(gen2)
    val div2RoundTowardsInfinity = Output(gen2)
    val div2RoundHalfDown = Output(gen2)
    val div2RoundHalfUp = Output(gen2)
    val div2RoundHalfTowardsZero = Output(gen2)
    val div2Round = Output(gen2)
    val div2Convergent = Output(gen2)
    val div2RoundHalfToOdd = Output(gen2)
    val div2NoTrim = Output(gen3)
  })

  val d = 2
  DspContext.withBinaryPointGrowth(0){
    val regDiv2Floor = RegNext(DspContext.withTrimType(RoundDown) {
      io.a.div2(d)
    })
    val regDiv2Ceiling = RegNext(DspContext.withTrimType(RoundUp) {
      io.a.div2(d)
    })
    val regDiv2RoundTowardsZero = RegNext(DspContext.withTrimType(RoundTowardsZero) {
      io.a.div2(d)
    })
    val regDiv2RoundTowardsInfinity = RegNext(DspContext.withTrimType(RoundTowardsInfinity) {
      io.a.div2(d)
    })
    val regDiv2RoundHalfDown = RegNext(DspContext.withTrimType(RoundHalfDown) {
      io.a.div2(d)
    })
    val regDiv2RoundHalfUp = RegNext(DspContext.withTrimType(RoundHalfUp) {
      io.a.div2(d)
    })
    val regDiv2RoundHalfTowardsZero = RegNext(DspContext.withTrimType(RoundHalfTowardsZero) {
      io.a.div2(d)
    })
    val regDiv2Round = RegNext(DspContext.withTrimType(RoundHalfTowardsInfinity) {
      io.a.div2(d)
    })
    val regDiv2Convergent = RegNext(DspContext.withTrimType(RoundHalfToEven) {
      io.a.div2(d)
    })
    val regDiv2RoundHalfToOdd = RegNext(DspContext.withTrimType(RoundHalfToOdd) {
      io.a.div2(d)
    })
    val regDiv2NoTrim = RegNext(DspContext.withTrimType(NoTrim) {
      io.a.div2(d)
    })

    io.div2Floor := regDiv2Floor
    io.div2Ceiling := regDiv2Ceiling
    io.div2RoundTowardsZero := regDiv2RoundTowardsZero
    io.div2RoundTowardsInfinity := regDiv2RoundTowardsInfinity
    io.div2RoundHalfDown := regDiv2RoundHalfDown
    io.div2RoundHalfTowardsZero := regDiv2RoundHalfTowardsZero
    io.div2Round := regDiv2Round
    io.div2Convergent := regDiv2Convergent
    io.div2RoundHalfUp := regDiv2RoundHalfUp
    io.div2RoundHalfToOdd := regDiv2RoundHalfToOdd
    io.div2NoTrim := regDiv2NoTrim
  }
}

class TrimTypeMultiplyCircuitTester[T <: Data : Ring]
(
  c: TrimTypeMultiplyCircuit[T],
  testVectors: (Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double)*
) extends DspTester(c) {
  for((a, b, m1, m2, m3, m4, m5, m6, m7, m8, m9, m10, m11) <- testVectors) {
    poke(c.io.a, a)
    poke(c.io.b, b)

    step(1)

    expect(c.io.multiplyFloor, m1)
    expect(c.io.multiplyCeiling, m2)
    expect(c.io.multiplyRoundTowardsZero, m3)
    expect(c.io.multiplyRoundTowardsInfinity, m4)
    expect(c.io.multiplyRoundHalfDown, m5)
    expect(c.io.multiplyRoundHalfUp, m6)
    expect(c.io.multiplyRoundHalfTowardsZero, m7)
    expect(c.io.multiplyRound, m8)
    expect(c.io.multiplyConvergent, m9)
    expect(c.io.multiplyRoundHalfToOdd, m10)
    expect(c.io.multiplyNoTrim, m11)
  }
}

class TrimTypeDiv2CircuitTester[T <: Data : Ring : BinaryRepresentation]
(
  c: TrimTypeDiv2Circuit[T],
  testVectors: (Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double, Double)*
) extends DspTester(c) {
  for((a, d1, d2, d3, d4, d5, d6, d7, d8, d9, d10, d11) <- testVectors) {
    poke(c.io.a, a)

    step(1)

    expect(c.io.div2Floor, d1)
    expect(c.io.div2Ceiling, d2)
    expect(c.io.div2RoundTowardsZero, d3)
    expect(c.io.div2RoundTowardsInfinity, d4)
    expect(c.io.div2RoundHalfDown, d5)
    expect(c.io.div2RoundHalfUp, d6)
    expect(c.io.div2RoundHalfTowardsZero, d7)
    expect(c.io.div2Round, d8)
    expect(c.io.div2Convergent, d9)
    expect(c.io.div2RoundHalfToOdd, d10)
    expect(c.io.div2NoTrim, d11)
  }
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

  val regAddWrap = RegNext(DspContext.withOverflowType(Wrap) { io.a context_+ io.b })
  val regAddGrow = RegNext(DspContext.withOverflowType(Grow) { io.a context_+ io.b })

  val regSubWrap = RegNext(DspContext.withOverflowType(Wrap) { io.a context_- io.b })
  val regSubGrow = RegNext(if (io.a.isInstanceOf[UInt]) 0.U else DspContext.withOverflowType(Grow) { io.a context_- io.b })
//  val regSubGrow = RegNext(DspContext.withOverflowType(Grow) { io.a context_- io.b })

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
  val r = RegNext(DspContext.withOverflowType(Grow) { io.a context_- io.b })
  io.o := r
}

class BinaryReprTester(c: BinaryRepr[UInt, SInt, FixedPoint]) extends DspTester(c) {
  poke(c.io.uIn, 0)
  expect(c.io.uOut, 0.0)

  poke(c.io.sIn, 0)
  expect(c.io.sOut, 0.0)

  poke(c.io.fIn, 0.0)
  expect(c.io.fOut, 0.0)

  step(1)

  poke(c.io.sIn, 1)
  expect(c.io.sOut, 0.0)

  poke(c.io.fIn, 1.0)
  expect(c.io.fOut, 0.0)

  step(1)

  poke(c.io.sIn, -1)
  expect(c.io.sOut, 1.0)

  poke(c.io.fIn, -1.0)
  expect(c.io.fOut, 1.0)

  step(1)

  poke(c.io.uIn, 3)
  expect(c.io.uDiv2, 0.0)

  poke(c.io.sIn, 3)
  expect(c.io.sDiv2, 0.0)

  poke(c.io.fIn, 3.5)
  expect(c.io.fOut, 0.0)

  step(1)
  poke(c.io.uIn, 48)
  expect(c.io.uDiv2, 12.0)

  poke(c.io.sIn, 32)
  expect(c.io.sDiv2,8.0)

  poke(c.io.fIn, 14.0)
  expect(c.io.fDiv2, 3.5)
}

class BinaryRepr[TU <: Data : RealBits, TS <: Data : RealBits, TF <: Data : RealBits]
(uGen: TU, sGen: TS, fGen: TF)
  extends Module {
  val io = IO(new Bundle {
    val uIn = Input(uGen)
    val sIn = Input(sGen)
    val fIn = Input(fGen)
    val uOut = Output(UInt(1.W))
    val sOut = Output(UInt(1.W))
    val fOut = Output(UInt(1.W))

    val uDiv2 = Output(uGen)
    val sDiv2 = Output(sGen)
    val fDiv2 = Output(fGen)

    val uMul2 = Output(UInt((uGen.getWidth * 2).W))
    val sMul2 = Output(SInt((sGen.getWidth * 2).W))
    val fMul2 = Output(FixedPoint(20.W, 2.BP))
  })

  io.uOut := io.uIn.signBit()
  io.sOut := io.sIn.signBit()
  io.fOut := io.fIn.signBit()

  io.uDiv2 := io.uIn.div2(2)
  io.sDiv2 := io.sIn.div2(2)
  io.fDiv2 := io.fIn.div2(2)

  io.uMul2 := io.uIn.mul2(2)
  io.sMul2 := io.sIn.mul2(2)
  io.fMul2 := io.fIn.mul2(2)
}
