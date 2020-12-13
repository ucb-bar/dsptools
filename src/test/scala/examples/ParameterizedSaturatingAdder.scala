// SPDX-License-Identifier: Apache-2.0

package examples

import chisel3._
import dsptools.{DspContext, DspTester, Saturate}
import dsptools.numbers._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ParameterizedSaturatingAdder[T <: Data:Integer](gen:() => T) extends Module {
  val io = IO(new Bundle {
    val a1: T =        Input(gen().cloneType)
    val a2: T =        Input(gen().cloneType)
    val normalSum  =   Output(gen().cloneType)
    val saturatedSum = Output(gen().cloneType)
  })

  val register1 = Reg(gen().cloneType)
  val register2 = Reg(gen().cloneType)

  println(s"ParameterizedSaturatingAdder ${DspContext.current}")
  register1 := io.a1 + io.a2

  DspContext.withOverflowType(Saturate) {
    register2 := io.a1 + io.a2
  }
  io.normalSum := register1
  io.saturatedSum := register2
}

class ParameterizedSaturatingAdderTester[T<:Data:Integer](c: ParameterizedSaturatingAdder[T], width: Int) extends DspTester(c) {
  val min = -(1 << (width - 1))
  val max = (1 << (width-1)) - 1
  println("Min = " + min.toString)
  println("Max = " + max.toString)
  def overflowint(x: Int): Int = {
    if (x > max) overflowint(min + x - max - 1)
    else if (x < min) overflowint(max + x - min + 1)
    else x
  }
  def saturateint(x: Int): Int = {
    if (x > max) max
    else if (x < min) min
    else x
  }
  for {
    i <- min to max
    j <- min to max
  } {
    println(s"I=$i and J=$j")
    poke(c.io.a1, i)
    poke(c.io.a2, j)
    step(1)

    val resultNormal = peek(c.io.normalSum)
    val resultSaturated = peek(c.io.saturatedSum)

    expect(c.io.normalSum, overflowint(i+j), s"parameterized normal adder $i + $j => $resultNormal should have been ${overflowint(i+j)}")
    expect(c.io.saturatedSum, saturateint(i+j), s"parameterized saturating adder $i + $j => $resultSaturated should have been ${saturateint(i+j)}")
  }
}

class ParameterizedSaturatingAdderSpec extends AnyFlatSpec with Matchers {
  behavior of "parameterized saturating adder circuit on SInt"

  ignore should "allow registers to be declared that infer widths" in {

    val width = 3
    def getSInt(): SInt = SInt(width.W)

    chisel3.iotesters.Driver(() => new ParameterizedSaturatingAdder(getSInt)) { c =>
      new ParameterizedSaturatingAdderTester(c, width)
    } should be (true)
  }

}

