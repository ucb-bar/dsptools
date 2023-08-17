// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3.{fromDoubleToLiteral => _, fromIntToBinaryPoint => _, _}
import fixedpoint._
import chisel3.experimental.{FixedPoint => _, _}
import dsptools.{DspContext, Grow, Wrap}
import org.scalatest.freespec.AnyFreeSpec
import chiseltest._
import chiseltest.iotesters._
import dsptools.misc.PeekPokeDspExtensions

class AbsSpec extends AnyFreeSpec with ChiselScalatestTester {
  "absolute value should work for all types" - {
    "abs should be obvious when not at extreme negative" - {
      "but returns a negative number for extreme value at max negative for SInt and FixedPoint" - {
        "with interpreter" in {
          test(new DoesAbs(UInt(4.W), SInt(4.W), FixedPoint(5.W, 2.BP)))
            .runPeekPoke(new DoesAbsTester(_))
        }
        "and with verilator" in {
          test(new DoesAbs(UInt(4.W), SInt(4.W), FixedPoint(5.W, 2.BP)))
            .withAnnotations(Seq(VerilatorBackendAnnotation))
            .runPeekPoke(new DoesAbsTester(_))
        }
      }
    }
  }

}

class DoesAbsTester(c: DoesAbs[UInt, SInt, FixedPoint]) extends PeekPokeTester(c) with PeekPokeDspExtensions {
  for (i <- BigDecimal(0.0) to 15.0 by 1.0) {
    poke(c.io.uIn, i)
    expect(c.io.uAbsGrow, i)
    expect(c.io.uAbsWrap, i)
    step(1)
  }
  for (i <- BigDecimal(-7.0) to 7.0 by 1.0) {
    poke(c.io.sIn, i)
    expect(c.io.sAbsGrow, i.abs)
    expect(c.io.sAbsWrap, i.abs)
    step(1)
  }
  poke(c.io.sIn, -8.0)
  expect(c.io.sAbsGrow, 8.0)
  expect(c.io.sAbsWrap, -8.0)

  val increment = 0.25

  for (i <- BigDecimal(-3.75) to 3.75 by increment) {
    poke(c.io.fIn, i)
    expect(c.io.fAbsGrow, i.abs)
    expect(c.io.fAbsWrap, i.abs)
    step(1)
  }
  poke(c.io.fIn, -4.0)
  expect(c.io.fAbsGrow, 4.0)
  expect(c.io.fAbsWrap, -4.0)
}

class DoesAbs[TU <: Data: Signed: Ring, TS <: Data: Signed: Ring, TF <: Data: Signed: Ring](
  uGen: TU,
  sGen: TS,
  fGen: TF)
    extends Module {
  val io = IO(new Bundle {
    val uIn = Input(uGen)
    val sIn = Input(sGen)
    val fIn = Input(fGen)

    val uAbsGrow = Output(uGen)
    val uAbsWrap = Output(uGen)

    val sAbsGrow = Output(SInt(5.W))
    val sAbsWrap = Output(SInt(4.W))

    val fAbsGrow = Output(FixedPoint(6.W, 2.BP))
    val fAbsWrap = Output(FixedPoint(5.W, 2.BP))
  })

  io.uAbsGrow := DspContext.withOverflowType(Grow) { io.uIn.context_abs }
  io.uAbsWrap := DspContext.withOverflowType(Wrap) { io.uIn.context_abs }

  io.sAbsGrow := DspContext.withOverflowType(Grow) { io.sIn.context_abs }
  io.sAbsWrap := DspContext.withOverflowType(Wrap) { io.sIn.context_abs }

  io.fAbsGrow := DspContext.withOverflowType(Grow) { io.fIn.context_abs }
  io.fAbsWrap := DspContext.withOverflowType(Wrap) { io.fIn.context_abs }
}
