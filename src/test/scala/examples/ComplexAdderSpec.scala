//// See LICENSE for license details.
//
package examples

import chisel3.core._
import chisel3.iotesters.{Backend}
import chisel3.{Bundle, Module}
import dsptools.{DspContext, DspTester}
import dsptools.numbers.{FixedPointRing, DspComplexRing, DspComplex}
import org.scalatest.{Matchers, FlatSpec}
import spire.implicits._


class SimpleComplexAdder extends Module {
  implicit val dspContext = DspContext()
  implicit val fixedPointEvidence = new FixedPointRing()
  implicit val ev: DspComplexRing[FixedPoint] = new DspComplexRing[FixedPoint]()

  val io = new Bundle {
    val a1 = DspComplex(FixedPoint(INPUT, 6, 4), FixedPoint(INPUT, 6, 4))
    val a2 = DspComplex(FixedPoint(INPUT, 8, 1), FixedPoint(INPUT, 8, 1))
    val c  = DspComplex(FixedPoint(OUTPUT, 12, 5), FixedPoint(OUTPUT, 12, 5))
  }
  //  spatialAssert(Seq(io.a1), Seq(io.c), 5)
  //  spatialAssert(Seq(io.a2), Seq(io.c), "group1")

  val register1 = Reg(io.c)

//  val registerReal = Reg(io.a1.real)
//  val registerImaginary = Reg(io.a1.imaginary)

  register1 := io.a1 + io.a2

  io.c := register1
}
class SimpleComplexAdderTester(c: SimpleComplexAdder) extends DspTester(c) {
  for {
    i <- 0.0 to 1.0 by 0.25
    j <- 0.0 to 4.0 by 0.5
  } {
    val expected = i + j

    poke(c.io.a1.real, i)
    poke(c.io.a1.imaginary, 0.0)
    poke(c.io.a2.real, j)
    poke(c.io.a2.imaginary, 0.0)
    step(1)

    println(s"SimpleComplexAdder: $i + $j should make $expected got ${peek(c.io.c.real)}")
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
