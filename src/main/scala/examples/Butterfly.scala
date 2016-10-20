// See LICENSE for license details.

// Author: Stevo Bailey (stevo.bailey@berkeley.edu)

package dsptools.examples

import chisel3.util.{Counter, ShiftRegister, log2Up}
import chisel3._
import dsptools.numbers.{DspComplex, Real}
import dsptools.numbers.implicits._

// butterfly io
class ButterflyIO[T<:Data:Real](genIn: => DspComplex[T],
                                genOut: => Option[DspComplex[T]] = None,
                                genTwiddle: => Option[DspComplex[T]] = None
                               ) extends Bundle {
  val in1 = Input(genIn)
  val in2 = Input(genIn)
  val out1 = Output(genOut.getOrElse(genIn))
  val out2 = Output(genOut.getOrElse(genIn))
  val twiddle = Input(genTwiddle.getOrElse(genIn))
}

// two input two output butterfly
// decimation-in-time structure
// w is the input twiddle factor
//
//   in1 -------      ------ out1
//              \    /
//               \  /
//                \/ +
//                /\ -
//               /  \
//              /    \
//   in2 ---w---      ------ out2
//

class Butterfly[T<:Data:Real](genIn: => DspComplex[T],
                              genOut: => Option[DspComplex[T]] = None,
                              genTwiddle: => Option[DspComplex[T]] = None
                             ) extends Module {
  val io = IO(new ButterflyIO(genIn, genOut, genTwiddle))

  // multiply
  val product = io.in2 * io.twiddle

  // add/subtract
  io.out1 := io.in1 + product
  io.out2 := io.in1 - product
}
