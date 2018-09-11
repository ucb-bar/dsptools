// See LICENSE for license details.

package examples

import chisel3.core._
import chisel3.iotesters.{Backend}
import chisel3.{Bundle, Module}
import dsptools.{DspContext, DspTester}
import dsptools.numbers._
import org.scalatest.{Matchers, FlatSpec}
import spire.algebra.Ring

//scalastyle:off magic.number
class SimpleComplexAdder extends Module {
  val io = IO(new Bundle {
    val a1 = Input(DspComplex(FixedPoint(6.W, 4.BP), FixedPoint(6.W, 4.BP)))
    val a2 = Input(DspComplex(FixedPoint(8.W, 1.BP), FixedPoint(8.W, 1.BP)))
    val c  = Output(DspComplex(FixedPoint(14.W, 5.BP), FixedPoint(14.W, 5.BP)))
  })
  //  spatialAssert(Seq(io.a1), Seq(io.c), 5)
  //  spatialAssert(Seq(io.a2), Seq(io.c), "group1")

  val register1 = Reg(io.c.cloneType)

//  val registerReal = Reg(io.a1.real)
//  val registerimag = Reg(io.a1.imag)

  register1 := io.a1 * io.a2

  io.c := register1
}
class SimpleComplexAdderTester(c: SimpleComplexAdder) extends DspTester(c) {
  for {
    i <- 0.0 to 1.0 by 0.25
    j <- 0.0 to 4.0 by 0.5
  } {
    val expected = i * j

    poke(c.io.a1.real, i)
    poke(c.io.a1.imag, 0.0)
    poke(c.io.a2.real, j)
    poke(c.io.a2.imag, 0.0)
    step(1)

    println(s"SimpleComplexAdder: $i * $j should make $expected got ${peek(c.io.c.real)}")
  }
}
class SimpleComplexAdderSpec extends FlatSpec with Matchers {
  behavior of "SimpleComplexAdder"

  it should "add complex numbers excellently" in {
    chisel3.iotesters.Driver(() => new SimpleComplexAdder) { c =>
      new SimpleComplexAdderTester(c)
    } should be(true)

  }
}
