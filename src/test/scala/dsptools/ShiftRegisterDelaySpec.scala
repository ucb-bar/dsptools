// SPDX-License-Identifier: Apache-2.0

package dsptools

import chisel3._
import chisel3.experimental.FixedPoint
import dsptools.numbers._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

//TODO: DspReal truncate, ceil
//TODO: FixedPoint ceil
//TODO: For truncate and ceil, compare delay between Fixed and Real

//scalastyle:off magic.number regex

class AbsCircuitWithDelays[T <: Data : Signed](gen: T, val delays: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(gen)
    val outContextAbs = Output(gen)
  })

  DspContext.withNumAddPipes(delays) {
    val con = io.in.context_abs()
    printf("io.in %d con %d\n", io.in.asUInt, con.asUInt)
    io.outContextAbs := con
  }
}

class CeilTruncateCircuitWithDelays(val delays: Int) extends Module {
  val io = IO(new Bundle {
    val inFixed = Input(FixedPoint(12.W, 4.BP))
    val inReal = Input(DspReal())
    val outFixedCeil = Output(FixedPoint(12.W, 4.BP))
    val outRealCeil = Output(DspReal())
    val outFixedTruncate = Output(FixedPoint(12.W, 4.BP))
    val outRealTruncate = Output(DspReal())
  })

  DspContext.withNumAddPipes(delays) {
    io.outFixedCeil := io.inFixed.ceil()
    io.outRealCeil := io.inReal.context_ceil()
    io.outFixedTruncate := io.inFixed.truncate()
    io.outRealTruncate := io.inReal.truncate()
  }
}
class CircuitWithDelaysTester[T <: Data : Signed](c: AbsCircuitWithDelays[T]) extends DspTester(c) {
  private val delaySize = c.delays

  def oneTest(): Unit = {
    def values: Seq[Double] = (BigDecimal(-delaySize) to delaySize.toDouble by 1.0).map(_.toDouble)
    val inQueue = new mutable.Queue[Double] ++ values
    val outQueue = new mutable.Queue[Double] ++ Seq.fill(delaySize-1)(0.0) ++ values.map(_.abs)

    while(inQueue.nonEmpty) {
      val inValue = inQueue.dequeue()
      poke(c.io.in, inValue)
      step(1)
      val expectedValue = outQueue.dequeue()
      expect(c.io.outContextAbs, expectedValue)
    }
    while(outQueue.nonEmpty) {
      val expectedValue = outQueue.dequeue()
      step(1)
      expect(c.io.outContextAbs, expectedValue)
    }
  }

  updatableDspVerbose.withValue(false) {
    reset()
    poke(c.io.in, 0.0)
    step(10)
    oneTest()
  }
}

class CeilTruncateTester(c: CeilTruncateCircuitWithDelays) extends DspTester(c) {
  private val delaySize = c.delays

  def oneTest(
               inFixedIo: FixedPoint, outFixedIo: FixedPoint,
               inRealIo: DspReal, outRealIo: DspReal,
               delaySize: Int): Unit = {
    def values: Seq[Double] = (BigDecimal(-delaySize) to delaySize.toDouble by 1.0).map(_.toDouble)
    val inQueue = new mutable.Queue[Double] ++ values
    val outQueue = new mutable.Queue[Double] ++ Seq.fill(delaySize)(0.0) ++ values.map(_.ceil)

    while(inQueue.nonEmpty) {
      val inValue = inQueue.dequeue()
      poke(inFixedIo, inValue)
      poke(inRealIo, inValue)
      val expectedValue = outQueue.dequeue()
      expect(outFixedIo, expectedValue)
      expect(outRealIo, expectedValue)
      step(1)
    }
    while(outQueue.nonEmpty) {
      val expectedValue = outQueue.dequeue()
      expect(outFixedIo, expectedValue)
      expect(outRealIo, expectedValue)
      step(1)
    }
  }

  updatableDspVerbose.withValue(false) {
    poke(c.io.inFixed, 0.0)
    poke(c.io.inReal, 0.0)
    reset()
    step(10)
    oneTest(c.io.inFixed, c.io.outFixedCeil, c.io.inReal, c.io.outRealCeil, delaySize)
  }
}

class ShiftRegisterDelaySpec extends AnyFreeSpec with Matchers {
  "ceil delay should be consistent between dsp real and fixed point" in {
    dsptools.Driver.execute(
      () => new CeilTruncateCircuitWithDelays(2),
      Array("--backend-name", "firrtl")
    ) { c =>
      new CeilTruncateTester(c)
    } should be(true)
  }
  "abs delays should be consistent across both sides of underlying mux" - {

    def sGen: SInt = SInt(16.W)
    def fGen: FixedPoint = FixedPoint(16.W, 8.BP)
    def rGen: DspReal = DspReal()

    "when used with SInt" in {
      dsptools.Driver.execute(
        () => new AbsCircuitWithDelays(sGen, 3),
        Array("--backend-name", "verilator")
      ) { c =>
        new CircuitWithDelaysTester(c)
      } should be(true)
    }

    "when used with FixedPoint" in {
      dsptools.Driver.execute(
        () => new AbsCircuitWithDelays(fGen, 3),
        Array("--backend-name", "verilator")
      ) { c =>
        new CircuitWithDelaysTester(c)
      } should be(true)
    }

    "when used with DspReal" in {
      dsptools.Driver.execute(
        () => new AbsCircuitWithDelays(rGen, 8),
        Array("--backend-name", "verilator")
      ) { c =>
        new CircuitWithDelaysTester(c)
      } should be(true)
    }
  }
}
