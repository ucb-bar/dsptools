// See LICENSE for license details.

package examples

import chisel3._
import chisel3.experimental.FixedPoint
import dsptools.DspTester
import dsptools.numbers._
import org.scalatest.{FlatSpec, Matchers}

//scalastyle:off magic.number

class InstrumentingAdder[T <: Data:Ring](gen:() => T) extends Module {
  val io = IO(new Bundle {
    val a1: T = Input(gen())
    val a2: T = Input(gen())
    val c     = Output(gen())
  })

  val register1 = Reg(gen())

  register1 := io.a1 + io.a2

  io.c := register1
}

class InstrumentingAdderTester[T<:Data:Ring](c: InstrumentingAdder[T]) extends DspTester(c) {
  for {
    i <- -2.0 to 1.0 by 0.25
    j <- -2.0 to 4.0 by 0.5
  } {
    poke(c.io.a1, i)
    poke(c.io.a2, j)
    step(1)

    val result = peek(c.io.c)

    expect(c.io.c, i + j, s"parameterize adder tester $i + $j => $result should have been ${i + j}")
  }
}

class InstrumentingAdderSpec extends FlatSpec with Matchers {

  behavior of "parameterized adder circuit on blackbox real"

  it should "run while being instrumented" in {
    def getFixed: FixedPoint = FixedPoint(32.W, 16.BP)

    dsptools.Driver.execute(() => new InstrumentingAdder(getFixed _),
      Array("-fimbu", "-fimof", "signals.csv", "-fimhb", "16")) { c =>
      new InstrumentingAdderTester(c)
    } should be (true)
  }
}
