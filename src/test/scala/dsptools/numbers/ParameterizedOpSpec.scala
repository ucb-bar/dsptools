// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import breeze.math.Complex
import chisel3.{fromDoubleToLiteral => _, fromIntToBinaryPoint => _, _}
import fixedpoint._
import chiseltest._
import chiseltest.iotesters._
import org.scalatest.freespec.AnyFreeSpec
import dsptools._
import dsptools.misc.PeekPokeDspExtensions

//scalastyle:off magic.number

class ParameterizedNumberOperation[T <: Data: Ring](
  inputGenerator:  () => T,
  outputGenerator: () => T,
  val op:          String = "+")
    extends Module {
  val io = IO(new Bundle {
    val a1: T = Input(inputGenerator().cloneType)
    val a2: T = Input(inputGenerator().cloneType)
    val c:  T = Output(outputGenerator().cloneType)
  })

  val register1 = Reg(outputGenerator().cloneType)

  register1 := {
    op match {
      case "+" => io.a1 + io.a2
      case "-" => io.a1 - io.a2
      case "*" => DspContext.withTrimType(NoTrim) { io.a1 * io.a2 }
      //      case "/" => io.a1 / io.a2
      case _ => throw new Exception(s"Bad operator $op passed to ParameterizedNumberOperation")
    }
  }

  io.c := register1
}

class ParameterizedOpTester[T <: Data: Ring](c: ParameterizedNumberOperation[T])
    extends PeekPokeTester(c)
    with PeekPokeDspExtensions {
  for {
    i <- BigDecimal(0.0) to 1.0 by 0.25
    j <- BigDecimal(0.0) to 4.0 by 0.5
  } {
    val expected = c.op match {
      case "+" => i + j
      case "-" => i - j
      case "*" => i * j
      case _   => i + j
    }

    poke(c.io.a1, i)
    poke(c.io.a2, j)
    step(1)

    val result = peek(c.io.c)

    expect(c.io.c, expected, s"$i ${c.op} $j => $result, should have been $expected")
  }
}

class ParameterizedOpSpec extends AnyFreeSpec with ChiselScalatestTester {
  """
  ParameterizedNumericOperation will
  """ - {
    def realGenerator():     DspReal = new DspReal
    def fixedInGenerator():  FixedPoint = FixedPoint(16.W, 8.BP)
    def fixedOutGenerator(): FixedPoint = FixedPoint(48.W, 8.BP)

    "process Real numbers with the basic mathematical operations" - {
      Seq("+", "-", "*").foreach { operation =>
        s"operation $operation should work for all inputs" in {
          test(new ParameterizedNumberOperation(realGenerator, realGenerator, operation))
            .withAnnotations(Seq(VerilatorBackendAnnotation))
            .runPeekPoke(new ParameterizedOpTester(_))
        }
      }
    }
    "process Fixed point numbers with the basic mathematical operations" - {
      Seq("+", "-", "*").foreach { operation =>
        s"operation $operation should work for all inputs" in {
          test(new ParameterizedNumberOperation(fixedInGenerator, fixedOutGenerator, operation))
            .runPeekPoke(new ParameterizedOpTester(_))
        }
      }
    }
  }
}

class ComplexOpTester[T <: DspComplex[_]](c: ParameterizedNumberOperation[T])
    extends PeekPokeTester(c)
    with PeekPokeDspExtensions {
  for {
    i <- (BigDecimal(-1.0) to 1.0 by 0.25).map(_.toDouble)
    j <- (BigDecimal(-4.0) to 4.0 by 0.5).map(_.toDouble)
  } {
    val c1 = Complex(i, j)
    val c2 = Complex(j, i)

    val expected = c.op match {
      case "+" => c1 + c2
      case "-" => c1 - c2
      case "*" => c1 * c2
      case _   => c1 + c2
    }

    poke(c.io.a1, c1)
    poke(c.io.a2, c2)
    step(1)

    val result = peek(c.io.c)

    expect(c.io.c, expected, s"$i ${c.op} $j => $result, should have been $expected")
  }
}

class ComplexOpSpec extends AnyFreeSpec with ChiselScalatestTester {
  """
  ParameterizedNumericOperation will
  """ - {
    def complexFixedGenerator(): DspComplex[FixedPoint] = {
      DspComplex(FixedPoint(16.W, 2.BP), FixedPoint(16.W, 2.BP))
    }

    def complexFixedOutputGenerator(): DspComplex[FixedPoint] = {
      DspComplex(FixedPoint(48.W, 4.BP), FixedPoint(48.W, 4.BP))
    }

    def complexRealGenerator(): DspComplex[DspReal] = {
      DspComplex(DspReal(1.0), DspReal(1.0))
    }

    "process DspComplex[Real] numbers with the basic mathematical operations" - {
      Seq("+", "-", "*").foreach { operation =>
        s"operation $operation should work for all inputs" in {
          test(new ParameterizedNumberOperation(complexRealGenerator, complexRealGenerator, operation))
            .withAnnotations(Seq(VerilatorBackendAnnotation))
            .runPeekPoke(new ComplexOpTester(_))
        }
      }
    }

    "process DspComplex[FixedPoint] numbers with the basic mathematical operations" - {
      Seq("+", "-", "*").foreach { operation =>
        s"operation $operation should work for all inputs" in {
          test(new ParameterizedNumberOperation(complexFixedGenerator, complexFixedOutputGenerator, operation))
            .runPeekPoke(new ComplexOpTester(_))
        }
      }
    }
  }
}
