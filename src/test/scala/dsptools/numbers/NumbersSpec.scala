// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import chisel3.util.RegNext
import dsptools._
import dsptools.numbers._
import chisel3.experimental.FixedPoint
import dsptools.numbers.implicits._
import org.scalatest.{FreeSpec, Matchers}

/**
  * This will attempt to follow the dsptools.numbers.README.md file as close as possible.
  */
class NumbersSpec extends FreeSpec with Matchers {
  "dsptools provides extensive tools for implementing well behaved mathematical operations" - {
    "the behavior of operators and methods can be controlled with the DspContext object" - {
      "trimType controls how a * b, a.trimBinary(n) and a.div2(n) should round results" - {
        "TrimType tests" in {
          def f(w: Int, b: Int): FixedPoint = FixedPoint(w.W, b.BP)
          dsptools.Driver.execute(
            () => new TrimTypeCircuit(f(4, 2), f(10, 5), f(10, 5))) { c =>
            new TrimTypeCircuitTester(c)
          } should be(true)
        }
      }
    }
  }
}

class TrimTypeCircuitTester[T <: Data : Ring](c: TrimTypeCircuit[T]) extends DspTester(c) {
  poke(c.io.a, 1.25)
  poke(c.io.a, 1.25)
  step(2)
  println(s"peek ${peek(c.io.multiplyRoundHalfUp)}")
  println(s"peek ${peek(c.io.multiplyNoTrim)}")
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
  regMultiplyNoTrim := regMultiplyNoTrim
}