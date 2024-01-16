// SPDX-License-Identifier: Apache-2.0

package examples

import chisel3._
import fixedpoint._
import chiseltest._
import chiseltest.iotesters.PeekPokeTester
import org.scalatest.flatspec.AnyFlatSpec
import dsptools.misc.PeekPokeDspExtensions

//noinspection TypeAnnotation
class SimpleAdder extends Module {
  val a1 = IO(Input(FixedPoint(6.W, 4.BP)))
  val a2 = IO(Input(FixedPoint(8.W, 1.BP)))
  val c = IO(Output(FixedPoint(12.W, 5.BP)))

  val register1 = Reg(FixedPoint())

  register1 := a1 + a2

  c := register1
}

class SimpleAdderTester(c: SimpleAdder) extends PeekPokeTester(c) with PeekPokeDspExtensions {
  for {
    i <- BigDecimal(0.0) to 1.0 by 0.25
    j <- BigDecimal(0.0) to 4.0 by 0.5
  } {
    val expected = i + j

    poke(c.a1, i)
    poke(c.a2, j)
    step(1)
    expect(c.c, expected, s"SimpleAdder: $i + $j should make $expected got ${peek(c.c)}")
  }
}
class SimpleAdderSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior.of("SimpleAdder")

  it should "add to numbers excellently" in {
    test(new SimpleAdder) //(new SimpleAdderTester(_))
      .runPeekPoke(new SimpleAdderTester(_))
  }
}
