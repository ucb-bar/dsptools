// SPDX-License-Identifier: Apache-2.0

package dsptools.examples

import chisel3._
import chisel3.util.Valid
import spire.algebra.Ring
import spire.implicits._
import spire.math.{ConvertableFrom, ConvertableTo}

// CTTSF[FixedPoint, Double]
// CTTSF[DspComplex[FixedPoint], scala.Complex[Double]]

// This style preferred:
// class CTTSF[T<:Data:Ring,V](i: T, o: T, val taps: Seq[V], conv: V=>T)

class ConstantTapTransposedStreamingFIR[T <: Data:Ring:ConvertableTo, V:ConvertableFrom](
    inputGenerator: T,
    outputGenerator: T,
    val taps: Seq[V])
  extends Module {

  val io = IO(new Bundle {
    val input  = Input(Valid(inputGenerator))
    val output = Output(Valid(outputGenerator))
  })

  val products: Seq[T] = taps.reverse.map { tap =>
    val t : T = implicitly[ConvertableTo[T]].fromType(tap)
    io.input.bits * t
  }

  val last = Reg[T](outputGenerator)
  val nextLast = products.reduceLeft { (left: T, right: T) =>
    val reg = Reg(left.cloneType)
    when (io.input.valid) {
      reg := left
    }
    reg + right
  }
  when(io.input.valid) {
    last := nextLast
  }

  io.output.bits := last
  io.output.valid := RegNext(io.input.valid)
}

class TransposedStreamingFIR[T <: Data:Ring](inputGenerator: => T, outputGenerator: => T,
                                        tapGenerator: => T, numberOfTaps: Int)
                                        extends Module {
  val io = IO(new Bundle {
    val input = Input(inputGenerator)                  // note, using as Input here, causes IntelliJ to not like '*'
    val output = Output(outputGenerator)
    val taps = Input(Vec(numberOfTaps, tapGenerator))  // note, using as Input here, causes IntelliJ to not like '*'
  })

  val products: Seq[T] = io.taps.reverse.map { tap: T =>
    io.input * tap
  }

  val last = Reg(products.head.cloneType)
  last := products.reduceLeft { (left: T, right: T) =>
    val reg = Reg(left.cloneType)
    reg := left
    reg + right
  }

  io.output := last
}
