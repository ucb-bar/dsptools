//// See LICENSE for license details.
//
package examples

import chisel3.core._
import chisel3.iotesters.{Backend}
import chisel3.{Bundle, Module}
import dsptools.DspTester
import org.scalatest.{Matchers, FlatSpec}

class SimpleAdder extends Module {
  val io = new Bundle {
    val a1 = FixedPoint(INPUT, 6, 4)
    val a2 = FixedPoint(INPUT, 8, 1)
    val c  = FixedPoint(OUTPUT, 12, 5)
  }

  val register1 = Reg(FixedPoint())

  register1 := io.a1 + io.a2

  io.c := register1
}
class SimpleAdderTester(c: SimpleAdder) extends DspTester(c) {
  poke(c.io.a1, 7.1)

  for {
    i <- 0 to 100 by 3
    j <- 0 to 100 by 7
  } {
    poke(c.io.a1, BigInt(i))
    poke(c.io.a2, BigInt(j))
    step(1)
    println(s"peek ${peek(c.io.c)}")
  }
}
class SimpleAdderSpec extends FlatSpec with Matchers {
  behavior of "SimpleAdder"

  it should "add to numbers excellently" in {
    chisel3.iotesters.Driver(() => new SimpleAdder) { c =>
      new SimpleAdderTester(c)
    } should be(true)

  }
}
