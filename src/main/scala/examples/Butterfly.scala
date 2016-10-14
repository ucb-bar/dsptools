// See LICENSE for license details.

// Author: Stevo Bailey (stevo.bailey@berkeley.edu)

package dsptools.examples

import chisel3.util.{Counter, ShiftRegister, log2Up}
import chisel3.{Bool, Bundle, Data, Module, Reg, UInt, Vec, Wire, when}
import dsptools.numbers.Real
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
                              genTwiddle: Option[DspComplex[T]] = None
                             ) extends Module {
  val io = new ButterflyIO(genIn, genOut getOrElse(genIn), genTwiddle getOrElse(genIn))

  // multiply
  val product = io.in2*io.twiddle

  // add/subtract
  io.out1 := io.in1+product
  io.out2 := io.in1-product
}

//class ButterflyTester(c: Butterfly, iw: Int, ow: Int, tw: Int) extends Tester(c) {
//
//  // [stevo]: doesn't work, but the butterfly works; just trust me!
//  if (ow < iw) {
//    println("[error] Butterfly output bitwidth cannot be less than input bitwidth!")
//  }
//  else {
//    // generate random inputs
//    val in1 = Array(1, 1)
//    val in2 = Array(1, 1)
//    val twiddle = Array(0, 1)
//    poke(c.io.in1, int_to_tc(in1, iw))
//    poke(c.io.in2, int_to_tc(in2, iw))
//    poke(c.io.twiddle, int_to_tc(twiddle, tw))
//
//    // calculate and assert outputs
//    //val scale = BigInt.apply(pow(2, iw+tw-3-max(iw,tw)+2+1).toInt)
//    //mult = Array(mult(0)/scale, mult(1)/scale)
//    //expect(c.io.out1, addComplexSInt(in1, mult))
//    //expect(c.io.out2, subComplexSInt(in1, mult))
//    peek (c.mult)
//    peek (c.io.out1)
//    peek (c.io.out2)
//
//    // step
//    step(1)
//  }
//}
