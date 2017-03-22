// See LICENSE for license details.

package dsptools

import chisel3._
import chisel3.core.FixedPoint
import dsptools.numbers.{DspReal, Signed}
import dsptools.numbers.implicits._
import org.scalatest.{FreeSpec, Matchers}

//TODO: DspReal truncate, ceil
//TODO: FixedPoint ceil
//TODO: For truncate and ceil, compare delay between Fixed and Real

//scalastyle:off magic.number regex

class CircuitWithDelays[T <: Data : Signed](gen: T) extends Module {
  val io = IO(new Bundle {
    val in = Input(gen)
    val out = Output(gen)
  })

  DspContext.withNumAddPipes(3) {
    io.out := io.in.context_abs()
  }
}

class CircuitWithDelaysTester[T <: Data : Signed](c: CircuitWithDelays[T]) extends DspTester(c) {
  reset(10)
  poke(c.io.in, 111.0)
  step(10)
  println(s"c.io.out ${peek(c.io.out)}")
  poke(c.io.in, -1.0)
  println(s"c.io.out ${peek(c.io.out)}")
  step(1)
  println(s"c.io.out ${peek(c.io.out)}")
  step(1)
  println(s"c.io.out ${peek(c.io.out)}")
  step(1)
  expect(c.io.out, 1.0)
  poke(c.io.in, 77.0)
  println(s"c.io.out ${peek(c.io.out)}")
  step(1)
  println(s"c.io.out ${peek(c.io.out)}")
  step(1)
  println(s"c.io.out ${peek(c.io.out)}")
  step(1)
  expect(c.io.out, 77.0)
}

class ShiftRegisterDelaySpec extends FreeSpec with Matchers {
  "abs delays should be consistent across both sides of underlying mux" - {

    def sGen: SInt = SInt(16.W)
    def fGen: FixedPoint = FixedPoint(16.W, 8.BP)
    def rGen: DspReal = DspReal()

    "when used with SInt" in {
      dsptools.Driver.execute(
        () => new CircuitWithDelays(sGen),
        Array("--backend-name", "firrtl")
      ) { c =>
        new CircuitWithDelaysTester(c)
      } should be(true)
    }

    "when used with FixedPoint" in {
      dsptools.Driver.execute(
        () => new CircuitWithDelays(fGen),
        Array("--backend-name", "firrtl")
      ) { c =>
        new CircuitWithDelaysTester(c)
      } should be(true)
    }

    "when used with DspReal" in {
      dsptools.Driver.execute(
        () => new CircuitWithDelays(rGen),
        Array("--backend-name", "firrtl")
      ) { c =>
        new CircuitWithDelaysTester(c)
      } should be(true)
    }
  }
}
