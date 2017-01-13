// See LICENSE for license details.

package examples

import chisel3._
import dsptools.DspTester
import dsptools.numbers._
import dsptools.numbers.implicits._
import org.scalatest.{FlatSpec, Matchers}
import spire.algebra.Ring

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
  for {
    i <- 0.0 to 1.0 by 0.25
    j <- 0.0 to 4.0 by 0.5
  } {
    dspPoke(c.io.a1, i)
    dspPoke(c.io.a2, j)
    step(1)

    val result = dspPeekDouble(c.io.c)

    dspExpect(c.io.c, i + j, s"parameterize adder tester $i + $j => $result should have been ${i + j}")
  }
}

class ParameterizedAdderSpec extends FlatSpec with Matchers {

  behavior of "parameterized adder circuit on blackbox real"

  it should "allow registers to be declared that infer widths" in {
    def getReal: DspReal = new DspReal

    dsptools.Driver.execute(() => new ParameterizedAdder(getReal _)) { c =>
      new ParameterizedAdderTester(c)
    } should be (true)
  }

  behavior of "parameterized adder circuit on fixed point"

  it should "allow registers to be declared that infer widths" in {
    def getFixed: FixedPoint = FixedPoint(width = 32, binaryPoint = 16)

    dsptools.Driver.execute(() => new ParameterizedAdder(getFixed _)) { c =>
      new ParameterizedAdderTester(c)
    } should be (true)
  }
}
