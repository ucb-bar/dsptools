// See LICENSE for license details.

package dsptools.examples

import chisel3.util.Valid
import chisel3.{Reg, Vec}
import chisel3.{Bundle, Data, Module, when}
import dsptools.numbers.Real
import dsptools.{DspContext, Grow}
import spire.algebra._
import spire.implicits._
import spire.math.{ConvertableFrom, ConvertableTo}

// CTTSF[FixedPoint, Double]
// CTTSF[DspComplex[FixedPoint], scala.Complex[Double]]

// This style preferred:
// class CTTSF[T<:Data:Ring,V](i: T, o: T, val taps: Seq[V], conv: V=>T)

class ConstantTapTransposedStreamingFIR[T <: Data:Ring:ConvertableTo, V:ConvertableFrom](inputGenerator: T, outputGenerator: T, val taps: Seq[V])
                                               extends Module {
  val io = new Bundle {
    val input = Valid(inputGenerator.asOutput).flip
    val output = Valid(outputGenerator.asOutput)
  }

  val products: Seq[T] = taps.reverse.map { tap =>
    val t : T = implicitly[ConvertableTo[T]].fromType(tap)
    io.input.bits * t
  }

  val last = Reg[T](outputGenerator)
  val nextLast = products.reduceLeft { (left: T, right: T) =>
    val reg = Reg(left)
    when (io.input.valid) {
      reg := left
    }
    reg + right
  }
  when(io.input.valid) {
    last := nextLast
  }

  io.output.bits := last
  io.output.valid := Reg(next=io.input.valid)
}

class TransposedStreamingFIR[T <: Data:Ring](inputGenerator: => T, outputGenerator: => T,
                                        tapGenerator: => T, numberOfTaps: Int)
                                        extends Module {
  val io = new Bundle {
    val input = inputGenerator.asInput                  // note, using as Input here, causes IntelliJ to not like '*'
    val output = outputGenerator.asOutput
    val taps = Vec(numberOfTaps, tapGenerator).asInput  // note, using as Input here, causes IntelliJ to not like '*'
  }

  val products: Seq[T] = io.taps.reverse.map { tap: T =>
    io.input * tap
  }

  val last = Reg(products.head)
  last := products.reduceLeft { (left: T, right: T) =>
    val reg = Reg(left)
    reg := left
    reg + right
  }

  io.output := last
}
