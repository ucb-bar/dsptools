// See LICENSE for license details.

package examples

import chisel3.core._
import dsptools.{DspContext, DspTester}
import dsptools.numbers._
import org.scalatest.{FlatSpec, Matchers}
import spire.algebra.Ring
import spire.implicits._

//scalastyle:off magic.number

class ParameterizedAdder[T <: Data:Ring](gen:() => T) extends Module {
  val io = new Bundle {
    val a1: T = gen().cloneType.flip()
    val a2: T = gen().cloneType.flip()
    val c  = gen().cloneType
  }

  val register1 = Reg(gen().cloneType)

  register1 := io.a1 + io.a2

  io.c := register1
}

class ParameterizedAdderTester[T<:Data:Ring](c: ParameterizedAdder[T]) extends DspTester(c) {
  for {
    i <- 0.0 to 1.0 by 0.25
    j <- 0.0 to 4.0 by 0.5
  } {
    poke(c.io.a1, i)
    poke(c.io.a2, j)
    step(1)

    val result = peek(c.io.c)

    println(s"peek $result")
  }
}

class ParameterizedAdderSpec extends FlatSpec with Matchers {
  behavior of "parameterized adder circuit on blackbox real"

  it should "allow registers to be declared that infer widths" in {
    implicit val DefaultDspContext = DspContext()
    implicit val evidence = new DspRealRing()(DefaultDspContext)

    def getReal(): DspReal = new DspReal

    chisel3.iotesters.Driver(() => new ParameterizedAdder(getReal)) { c =>
      new ParameterizedAdderTester(c)
    } should be (true)
  }

  behavior of "parameterized adder circuit on fixed point"

  it should "allow registers to be declared that infer widths" in {
    implicit val DefaultDspContext = DspContext()
    implicit val evidence = new FixedPointRing()(DefaultDspContext)

    def getFixed(): FixedPoint = FixedPoint(OUTPUT, width = 32, binaryPoint = 16)

    chisel3.iotesters.Driver(() => new ParameterizedAdder(getFixed)) { c =>
      new ParameterizedAdderTester(c)
    } should be (true)
  }

  behavior of "parameterized adder circuit on complex"

  it should "allow registers to be declared that infer widths" in {
    implicit val DefaultDspContext = DspContext()
    implicit val fixedEvidence = new FixedPointRing()(DefaultDspContext)
    implicit val evidence = new DspComplexRing[FixedPoint]()(fixedEvidence, DefaultDspContext)

    def getComplex(): DspComplex[FixedPoint] = {
      DspComplex(
        FixedPoint(OUTPUT, width = 65, binaryPoint = 16),
        FixedPoint(OUTPUT, width = 65, binaryPoint = 16))
    }

    chisel3.iotesters.Driver(() => new ParameterizedAdder(getComplex)) { c =>
      new ParameterizedAdderTester(c)
    } should be (true)
  }

  behavior of "parameterized adder circuit on complex with reals"

  it should "allow registers to be declared that infer widths" in {
    implicit val DefaultDspContext = DspContext()
    implicit val fixedEvidence = new DspRealRing()(DefaultDspContext)
    implicit val evidence = new DspComplexRing[DspReal]()(fixedEvidence, DefaultDspContext)

    def getComplex(): DspComplex[DspReal] = {
      DspComplex(
        DspReal(1.0),
        DspReal(1.0))
    }

    chisel3.iotesters.Driver(() => new ParameterizedAdder(getComplex)) { c =>
      new ParameterizedAdderTester(c)
    } should be (true)
  }
}
