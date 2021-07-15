// SPDX-License-Identifier: Apache-2.0

package examples

import chisel3._
import chisel3.experimental.FixedPoint
import dsptools.DspTester
import dsptools.numbers._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

//scalastyle:off magic.number

class ParameterizedAdder[T <: Data:Ring](gen:() => T) extends Module {
  val io = IO(new Bundle {
    val a1: T = Input(gen().cloneType)
    val a2: T = Input(gen().cloneType)
    val c     = Output(gen().cloneType)
  })

  val register1 = Reg(gen().cloneType)

  register1 := io.a1 + io.a2

  io.c := register1
}

class ParameterizedAdderTester[T<:Data:Ring](c: ParameterizedAdder[T]) extends DspTester(c) {
  updatableDspVerbose.withValue(false) {
    for {
      i <- (BigDecimal(-2.0) to 1.0 by 0.25).map(_.toDouble)
      j <- (BigDecimal(-2.0) to 4.0 by 0.5).map(_.toDouble)
    } {
      poke(c.io.a1, i)
      poke(c.io.a2, j)
      step(1)

      val result = peek(c.io.c)

      expect(c.io.c, i + j, s"parameterize adder tester $i + $j => $result should have been ${i + j}")
    }
  }
}

class ParameterizedAdderSpec extends AnyFlatSpec with Matchers {

  behavior of "parameterized adder circuit on blackbox real"

  it should "allow registers to be declared that infer widths" in {
    def getReal: DspReal = new DspReal

    dsptools.Driver.execute(() => new ParameterizedAdder(getReal _)) { c =>
      new ParameterizedAdderTester(c)
    } should be (true)
  }

  behavior of "parameterized adder circuit on fixed point"

  it should "allow registers to be declared that infer widths" in {
    def getFixed: FixedPoint = FixedPoint(32.W, 16.BP)

    dsptools.Driver.execute(() => new ParameterizedAdder(getFixed _)) { c =>
      new ParameterizedAdderTester(c)
    } should be (true)

    dsptools.Driver.execute(() => new ParameterizedAdder(getFixed _), Array("--backend-name", "verilator")) { c =>
      new ParameterizedAdderTester(c)
    } should be (true)
  }
}
