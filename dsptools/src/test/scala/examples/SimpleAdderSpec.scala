//// See LICENSE for license details.
//
package examples

import chisel3.core._
import chisel3.{Bundle, Module}
import dsptools.DspTester
import org.scalatest.{Matchers, FlatSpec}

class SimpleAdder extends Module {
  val io = IO(new Bundle {
    val a1 = Input(FixedPoint(6.W, 4.BP))
    val a2 = Input(FixedPoint(8.W, 1.BP))
    val c  = Output(FixedPoint(12.W, 5.BP))
  })
//  spatialAssert(Seq(io.a1), Seq(io.c), 5)
//  spatialAssert(Seq(io.a2), Seq(io.c), "group1")

  val register1 = Reg(FixedPoint())

  register1 := io.a1 + io.a2

  io.c := register1
}
class SimpleAdderTester(c: SimpleAdder) extends DspTester(c) {
  for {
    i <- 0.0 to 1.0 by 0.25
    j <- 0.0 to 4.0 by 0.5
  } {
    val expected = i + j

    poke(c.io.a1, i)
    poke(c.io.a2, j)
    step(1)

    println(s"SimpleAdder: $i + $j should make $expected got ${peek(c.io.c)}")
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
