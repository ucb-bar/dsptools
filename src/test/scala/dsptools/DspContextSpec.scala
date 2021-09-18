// SPDX-License-Identifier: Apache-2.0

package dsptools

import chisel3._
import dsptools.numbers._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.parallel.CollectionConverters.RangeIsParallelizable

class DspContextSpec extends AnyFreeSpec with Matchers {
  "Context handling should be unobtrusive and convenient" - {
    "There should be a default available at all times" in {
      DspContext.current.binaryPoint should be (DspContext.defaultBinaryPoint)
    }

    "it can be very to override for simple alterations" in {
      DspContext.current.binaryPoint should be (DspContext.defaultBinaryPoint)

      DspContext.withBinaryPoint(-22) {
        DspContext.current.binaryPoint.get should be (-22)
      }

      DspContext.current.binaryPoint should be (DspContext.defaultBinaryPoint)
    }

    "it should be easy to override when using multiples" in {
      DspContext.current.binaryPoint should be (DspContext.defaultBinaryPoint)
      DspContext.current.overflowType should be (DspContext.defaultOverflowType)

      DspContext.alter(DspContext.current.copy(binaryPoint = Some(77), overflowType = Saturate)) {
        DspContext.current.binaryPoint.get should be (77)
        DspContext.current.overflowType should be (Saturate)
      }

      DspContext.current.binaryPoint should be (DspContext.defaultBinaryPoint)
      DspContext.current.overflowType should be (DspContext.defaultOverflowType)
    }

    "it should work multi-threaded and return values of block" ignore {
      DspContext.current.numBits should be (DspContext.defaultNumBits)

      val points = (1 to 100).par.map { n =>
        DspContext.withNumBits(n) {
          DspContext.current.numBits.get should be (n)
          n * n
        }
      }

      val zipped = points.zipWithIndex
      zipped.foreach {
        case (p: Int, i: Int) => p should be (math.pow(i + 1, 2))
      }

      DspContext.current.numBits should be (DspContext.defaultNumBits)
    }
  }

  "Test proper nesting of DspContext over module instantiation" in {
    dsptools.Driver.execute(
      () => new ContextNestingTop(UInt(4.W), UInt(5.W)),
      Array("--backend-name", "firrtl")
    ) { c =>
      new ContextNestingTester(c)
    } should be(true)  }
}

class ContextNestingTester(c: ContextNestingTop[UInt]) extends DspTester(c) {
  poke(c.io.in1, 15.0)
  poke(c.io.in2, 2.0)

  expect(c.io.mod1Default, 1.0)
  expect(c.io.mod1Wrap, 1.0)
  expect(c.io.mod1Grow, 17.0)
  expect(c.io.mod2Default, 17.0)
  expect(c.io.mod2Wrap, 1.0)
  expect(c.io.mod2Grow, 17.0)
}

class ContextNestingBottom[T <: Data : Ring](gen1: T, gen2: T) extends Module {
  val io = IO( new Bundle {
    val in1 = Input(gen1)
    val in2 = Input(gen1)
    val outDefault = Output(gen2)
    val outWrap    = Output(gen2)
    val outGrow    = Output(gen2)
  })

  DspContext.withOverflowType(Wrap) {
    io.outWrap := io.in1 context_+ io.in2
  }
  DspContext.withOverflowType(Grow) {
    io.outGrow := io.in1 context_+ io.in2
  }

  io.outDefault := io.in1 context_+ io.in2
}

class ContextNestingTop[T <: Data : Ring](gen1: T, gen2: T) extends Module {
  val io = IO( new Bundle {
    val in1 = Input(gen1)
    val in2 = Input(gen1)
    val mod1Default = Output(gen2)
    val mod1Wrap    = Output(gen2)
    val mod1Grow    = Output(gen2)
    val mod2Default = Output(gen2)
    val mod2Wrap    = Output(gen2)
    val mod2Grow    = Output(gen2)
  })

  private val mod1 = DspContext.withOverflowType(Wrap) { Module(new ContextNestingBottom(gen1, gen2)) }
  private val mod2 = DspContext.withOverflowType(Grow) { Module(new ContextNestingBottom(gen1, gen2)) }

  mod1.io.in1 := io.in1
  mod1.io.in2 := io.in2
  mod2.io.in1 := io.in1
  mod2.io.in2 := io.in2

  io.mod1Default := mod1.io.outDefault
  io.mod1Wrap    := mod1.io.outWrap
  io.mod1Grow    := mod1.io.outGrow
  io.mod2Default := mod2.io.outDefault
  io.mod2Wrap    := mod2.io.outWrap
  io.mod2Grow    := mod2.io.outGrow
}
