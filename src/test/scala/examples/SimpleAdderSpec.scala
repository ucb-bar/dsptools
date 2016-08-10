//// See LICENSE for license details.
//
package examples

import chisel3.core._
import chisel3.iotesters.{runPeekPokeTester, Backend, PeekPokeTester}
import chisel3.{Bundle, Module}
import examples.StreamingAutocorrelator
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
class SimpleAdderTester(c: SimpleAdder, backend: Option[Backend] = None) extends PeekPokeTester(c, _backend = backend) {
  poke(c.io.a1, 4)
  poke(c.io.a2, 0x30)
  step(1)
  println(s"peek ${peek(c.io.c)}")
}
class SimpleAdderSpec extends FlatSpec with Matchers {
  behavior of "SimpleAdder"

  it should "add to numbers excellently" in {
    runPeekPokeTester(
      () => new SimpleAdder, "firrtl") {
      (c, b) => new SimpleAdderTester(c, b)
    } should be(true)

  }
}
