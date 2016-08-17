//// See LICENSE for license details.
//
package examples

import chisel3.core._
import chisel3.{Bundle, Module}
import dsptools.numbers._
import dsptools.{DspContext, DspTester}
import org.scalatest.{FlatSpec, Matchers}
import spire.implicits._


class SimpleOneArgBundle extends Module {
  implicit val dspContext = DspContext()
  implicit val fixedPointEvidence = new FixedPointRing()
  implicit val ev: OneArgBundleRing[FixedPoint] = new OneArgBundleRing[FixedPoint]()

  val io = new Bundle {
    val a1 = new OneArgBundle(FixedPoint(INPUT, 6, 4))
    val a2 = new OneArgBundle(FixedPoint(INPUT, 8, 1))
    val c  = new OneArgBundle(FixedPoint(OUTPUT, 12, 5))
  }
  //  spatialAssert(Seq(io.a1), Seq(io.c), 5)
  //  spatialAssert(Seq(io.a2), Seq(io.c), "group1")

  val register1 = Reg(io.a1)

  //  val registerReal = Reg(io.a1.real)
  //  val registerImaginary = Reg(io.a1.imaginary)

  register1 <> io.a1 + io.a2

  io.c := register1
}

class SimpleOneArgBundleTester(c: SimpleOneArgBundle) extends DspTester(c) {
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

    println(s"SimpleOneArgBundle: $i + $j should make $expected got ${peek(c.io.c.real)}")
  }
}

class SimpleOneArgBundleSpec extends FlatSpec with Matchers {
  behavior of "SimpleOneArgBundle"

  it should "add complex numbers excellently" in {
    chisel3.iotesters.Driver(() => new SimpleOneArgBundle) { c =>
      new SimpleOneArgBundleTester(c)
    } should be(true)

  }
}
