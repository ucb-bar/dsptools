// SPDX-License-Identifier: Apache-2.0

package examples

import chisel3._
import chisel3.experimental.FixedPoint
import chisel3.iotesters.Backend
import chisel3.{Bundle, Module}
import dsptools.{DspContext, DspTester}
import dsptools.numbers._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
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
    i <- DoubleRangeTo(0.0, 1.0, 0.25)
    j <- DoubleRangeTo(0.0, 4.0, 0.5)
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
class SimpleComplexAdderSpec extends AnyFlatSpec with Matchers {
  behavior of "SimpleComplexAdder"

  it should "add complex numbers excellently" in {
    chisel3.iotesters.Driver(() => new SimpleComplexAdder) { c =>
      new SimpleComplexAdderTester(c)
    } should be(true)

  }
}
