// SPDX-License-Identifier: Apache-2.0

package examples

import chisel3.{fromDoubleToLiteral => _, fromIntToBinaryPoint => _, _}
import fixedpoint._
import chiseltest._
import chiseltest.iotesters._
import chisel3.{Bundle, Module}
import dsptools.misc.PeekPokeDspExtensions
import dsptools.numbers._
import org.scalatest.flatspec.AnyFlatSpec

//scalastyle:off magic.number
class SimpleComplexMultiplier extends Module {
  val io = IO(new Bundle {
    val a1 = Input(DspComplex(FixedPoint(6.W, 4.BP), FixedPoint(6.W, 4.BP)))
    val a2 = Input(DspComplex(FixedPoint(8.W, 1.BP), FixedPoint(8.W, 1.BP)))
    val c = Output(DspComplex(FixedPoint(14.W, 5.BP), FixedPoint(14.W, 5.BP)))
  })
  //  spatialAssert(Seq(io.a1), Seq(io.c), 5)
  //  spatialAssert(Seq(io.a2), Seq(io.c), "group1")

  val register1 = Reg(io.c.cloneType)

  //  val registerReal = Reg(io.a1.real)
  //  val registerimag = Reg(io.a1.imag)

  register1 := io.a1 * io.a2

  io.c := register1
}

class SimpleComplexMultiplierTester(c: SimpleComplexMultiplier) extends PeekPokeTester(c) with PeekPokeDspExtensions {
  for {
    i <- (BigDecimal(0.0) to 1.0 by 0.25).map(_.toDouble)
    j <- (BigDecimal(0.0) to 4.0 by 0.5).map(_.toDouble)
  } {
    val expected = i * j

    poke(c.io.a1.real, i)
    poke(c.io.a1.imag, 0.0)
    poke(c.io.a2.real, j)
    poke(c.io.a2.imag, 0.0)
    step(1)

    expect(c.io.c.real, i * j)

    println(s"SimpleComplexMultiplier: $i * $j should make $expected got ${peek(c.io.c.real)}")
  }
}

class SimpleComplexMultiplierSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior.of("SimpleComplexMultiplier")

  it should "multiply complex numbers excellently" in {
    test(new SimpleComplexMultiplier)
      .runPeekPoke(new SimpleComplexMultiplierTester(_))
  }
}
