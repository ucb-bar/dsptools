// See LICENSE for license details.

// Author: Stevo Bailey (stevo.bailey@berkeley.edu)

package dsptools.examples

import chisel3.util.{Counter, ShiftRegister, log2Up}
import chisel3.{Bool, Bundle, Data, Module, Reg, UInt, Vec, Wire, when}
import dsptools.numbers.{DspComplex, Real}
import dsptools.numbers.implicits._

// butterfly io
class ButterflyIO[T<:Data:Real](genIn: => DspComplex[T], 
                                genOut: => Option[DspComplex[T]] = None, 
                                genTwiddle: => Option[DspComplex[T]] = None
                               ) extends Bundle {
  val in1 = genIn.asInput
  val in2 = genIn.asInput
  val out1 = genOut.getOrElse(genIn).asOutput
  val out2 = genOut.getOrElse(genIn).asOutput
  val twiddle = genTwiddle.getOrElse(genIn).asInput
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
  val io = new ButterflyIO(genIn, genOut, genTwiddle)

  // multiply
  val product = io.in2*io.twiddle

  // add/subtract
  io.out1 := io.in1+product
  io.out2 := io.in1-product
}
