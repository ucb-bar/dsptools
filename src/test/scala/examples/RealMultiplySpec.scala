// See LICENSE for license details.

package examples

import chisel3.core._
import chisel3.iotesters.{Backend, PeekPokeTester}
import dsptools.DspTester
import dsptools.numbers.DspReal
import org.scalatest.{FlatSpec, Matchers}

class RealMultiply extends Module {
  val io = new Bundle {
    val a1 = (new DspReal).flip()
    val a2 = (new DspReal).flip()
    val c  = new DspReal
  }

  val register1 = Reg(new DspReal)

  register1 := io.a1 * io.a2

  io.c := register1
}

class RealMultiplyTester(c: RealMultiply) extends DspTester(c) {
  for {
    i <- 0.0 to 1.0 by 0.25
    j <- 0.0 to 4.0 by 0.5
  } {
    poke(c.io.a1, i)
    poke(c.io.a2, j)
    step(1)

    val result = peek(c.io.c)

    expect(
      c.io.c == i * j,
      s"Multiply test: $i * $j => $result, expected ${i * j}"
    )
  }
}


class RealMultiplySpec extends FlatSpec with Matchers {
  behavior of "multiply circuit on blackbox real"

  it should "test a number of multiplies that happen in black boxes" in {
    chisel3.iotesters.Driver(() => new RealMultiply) { c =>
      new RealMultiplyTester(c)
    } should be (true)
  }
}
