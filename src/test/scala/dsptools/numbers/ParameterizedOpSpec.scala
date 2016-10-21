// See LICENSE for license details.

package dsptools.numbers

import breeze.math.Complex
import chisel3._
import dsptools.DspTester
import org.scalatest.{FreeSpec, Matchers}
import spire.algebra.Ring
import dsptools.numbers._
import dsptools.numbers.implicits._

class ParameterizedNumberOperation[T <: Data:Ring](
                                        inputGenerator:() => T,
                                        outputGenerator:() => T,
                                        val op: String = "+"
                                        ) extends Module {
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
      case "*" => io.a1 * io.a2
//      case "/" => io.a1 / io.a2
      case _ => throw new Exception(s"Bad operator $op passed to ParameterizedNumberOperation")
    }
  }

  io.c := register1
}

class ParameterizedOpTester[T<:Data:Ring](c: ParameterizedNumberOperation[T]) extends DspTester(c) {
  for {
    i <- 0.0 to 1.0 by 0.25
    j <- 0.0 to 4.0 by 0.5
  } {
    val expected = c.op match {
      case "+" => i + j
      case "-" => i - j
      case "*" => i * j
      case _ => i + j
    }
    dspPoke(c.io.a1, i)
    dspPoke(c.io.a2, j)
    step(1)

    val result = dspPeekDouble(c.io.c)

    dspExpect(c.io.c, expected, s"$i ${c.op} $j => $result, should have been $expected")
  }
}

class ParameterizedOpSpecification extends FreeSpec with Matchers {
  """
  The ParameterizedNumericOperation demonstrates a Module that can be instantiated to
  handle different numeric types and different numerical operations
  """ -
    {
    def realGenerator():  DspReal    = new DspReal
    def fixedInGenerator(): FixedPoint = FixedPoint(width = 16, binaryPoint = 8)
    def fixedOutGenerator(): FixedPoint = FixedPoint(width = 48, binaryPoint = 8)

    "This instance will process Real numbers with the basic mathematical operations" - {
      Seq("+", "-", "*").foreach { operation =>
        s"operation $operation should work for all inputs" in {
          chisel3.iotesters.Driver(
            () => new ParameterizedNumberOperation(realGenerator, realGenerator, operation)) { c =>
            new ParameterizedOpTester(c)
          } should be(true)
        }
      }
    }
    "This instance will process Fixed point numbers with the basic mathematical operations" - {
      Seq("+", "-", "*").foreach { operation =>
        s"operation $operation should work for all inputs" in {
          chisel3.iotesters.Driver(() => new ParameterizedNumberOperation(fixedInGenerator,
            fixedOutGenerator,
            operation)) { c =>
            new ParameterizedOpTester(c)
          } should be(true)
        }
      }
    }
  }
}

class ComplexOpTester[T<:DspComplex[_]](c: ParameterizedNumberOperation[T]) extends DspTester(c) {
  for {
    i <- -1.0 to 1.0 by 0.25
    j <- -4.0 to 4.0 by 0.5
  } {
    val c1 = Complex(i, j)
    val c2 = Complex(j, i)

    val expected = c.op match {
      case "+" => c1 + c2
      case "-" => c1 - c2
      case "*" => c1 * c2
      case _ => c1 + c2
    }
    dspPoke(c.io.a1, c1)
    dspPoke(c.io.a2, c2)
    step(1)

    val result = dspPeek(c.io.c).right.get

    dspExpect(c.io.c, expected, s"$i ${c.op} $j => $result, should have been $expected")
  }
}

class ComplexOpSpecification extends FreeSpec with Matchers {
  """
  The ParameterizedNumericOperation demonstrates a Module that can be instantiated to
  handle different numeric types and different numerical operations
  """ - {
    def complexFixedGenerator(): DspComplex[FixedPoint] = {
      DspComplex(
        FixedPoint(width = 16, binaryPoint = 2),
        FixedPoint(width = 16, binaryPoint = 2))
    }
    def complexFixedOutputGenerator(): DspComplex[FixedPoint] = {
      DspComplex(
        FixedPoint(width = 48, binaryPoint = 4),
        FixedPoint(width = 48, binaryPoint = 4))
    }
    def complexRealGenerator(): DspComplex[DspReal] = {
      DspComplex(
        DspReal(1.0),
        DspReal(1.0))
    }

//    "Run Repl for complexReal" in {
//      dsptools.Driver.executeFirrtlRepl(
//        () => new ParameterizedNumberOperation(complexRealGenerator, complexRealGenerator, "+")
//      )
//    }

    "This instance will process DspComplex[Real] numbers with the basic mathematical operations" - {
      Seq("+", "-", "*").foreach { operation =>
        s"operation $operation should work for all inputs" in {
          chisel3.iotesters.Driver(() => new ParameterizedNumberOperation(complexRealGenerator,
            complexRealGenerator,
            operation)) { c =>
            new ComplexOpTester(c)
          } should be(true)
        }
      }
    }
    "This instance will process DspComplex[FixedPoint] numbers with the basic mathematical operations" - {
      Seq("+", "-", "*").foreach { operation =>
        s"operation $operation should work for all inputs" in {
          chisel3.iotesters.Driver(() => new ParameterizedNumberOperation(complexFixedGenerator,
            complexFixedOutputGenerator,
            operation)) { c =>
            new ComplexOpTester(c)
          } should be(true)
        }
      }
    }
  }
}
