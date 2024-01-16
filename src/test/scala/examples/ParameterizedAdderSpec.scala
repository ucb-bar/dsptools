// SPDX-License-Identifier: Apache-2.0

package examples

import chisel3._
import fixedpoint._
import chiseltest._
import chiseltest.iotesters.PeekPokeTester
import dsptools.misc.PeekPokeDspExtensions
import dsptools.numbers._
import org.scalatest.flatspec.AnyFlatSpec

//noinspection TypeAnnotation
class ParameterizedAdder[T <: Data: Ring](gen: () => T) extends Module {
  val a1: T = IO(Input(gen().cloneType))
  val a2: T = IO(Input(gen().cloneType))
  val c = IO(Output(gen().cloneType))

  val register1 = Reg(gen().cloneType)

  register1 := a1 + a2

  c := register1
}

class ParameterizedAdderTester[T <: Data: Ring](c: ParameterizedAdder[T])
    extends PeekPokeTester(c)
    with PeekPokeDspExtensions {
  for {
    i <- (BigDecimal(-2.0) to 1.0 by 0.25).map(_.toDouble)
    j <- (BigDecimal(-2.0) to 4.0 by 0.5).map(_.toDouble)
  } {
    poke(c.a1, i)
    poke(c.a2, j)
    step(1)

    val result = peek(c.c)

    expect(c.c, i + j, s"parameterize adder tester $i + $j => $result should have been ${i + j}")
  }
}

class ParameterizedAdderSpec extends AnyFlatSpec with ChiselScalatestTester {

  behavior.of("parameterized adder circuit on blackbox real")

  it should "allow registers to be declared that infer widths" in {
    def getReal: DspReal = new DspReal

    test(new ParameterizedAdder(() => getReal))
      .withAnnotations(Seq(VerilatorBackendAnnotation))
      .runPeekPoke(new ParameterizedAdderTester(_))
  }

  behavior.of("parameterized adder circuit on fixed point")

  it should "allow registers to be declared that infer widths" in {
    def getFixed: FixedPoint = FixedPoint(32.W, 16.BP)

    test(new ParameterizedAdder(() => getFixed)).runPeekPoke(new ParameterizedAdderTester(_))

    test(new ParameterizedAdder(() => getFixed))
      .withAnnotations(Seq(VerilatorBackendAnnotation))
      .runPeekPoke(new ParameterizedAdderTester(_))
  }
}
