// See LICENSE for license details.

package dsptools.examples

import chisel3.util.{Counter, ShiftRegister, log2Up}
import chisel3.{Bool, Bundle, Data, Module, Reg, UInt, Vec, Wire, when}
import dsptools.numbers.{Integral}
//import spire.algebra.{Order => _, _}
//import spire.implicits._
import dsptools.numbers.implicits._

// polyphase filter bank io
class PFBIO[T<:Data](genIn: => T, genOut: => Option[T] = None, n: Int, p: Int) extends Bundle {
  val data_in = Vec.fill(p) { genIn.asInput }
  val data_out = Vec.fill(p) { genOut.getOrElse(genIn).asOutput }
  val sync_in = UInt(log2Up(n/p))
  val overflow = Bool()
}

// polyphase filter bank
// cw = coefficient bitwidth
// taps = number of FIR filter taps
// n = window size
// p = parallelism
// pipe = pipeline depth for the PFB
// use_sp_mem = use single-ported memories?
// the following two options are mutually exclusive (changes takes precedence):
// symm = use symmetric coefficients to save memory
// changes = use changes in coefficient values to save memory (ROM)
class PFB[T<:Data:Integral](genIn: => T, genOut: => Option[T],
                        n: Int, p: Int, min_mem_depth: Int,
                        taps: Int, pipe: Int, use_sp_mem: Boolean,
                        symm: Boolean = false, changes: Boolean = true)  extends Module {
  val io = new PFBIO(genIn, genOut, n, p)

  val bp = n/p

  val coeffs_array = scala.io.Source.fromFile("pfbcoeff.csv").getLines.toSeq.map(_.split(",").map(_.toInt))
  val coeffs_vec = Vec( coeffs_array.map( line => Vec ( line.map ( num => {
    val w = Wire(genIn)
    w := Integral[T].fromInt(num)
    w
  }
  ) ) ) )

  // replicate sync counter to avoid critical path when coefficients are calculated
  // far away from the multipliers. this guy is used for the coefficients
  val (sync_in_repl, dummy) = Counter(Bool(true), bp)
  when (io.sync_in === UInt(0)) {
    sync_in_repl := UInt(1)
  }

  // since coefficients change rarely, save memory by just storing change indices
  // assumes there are enough points that each change is either +1 or -1 (at least 64-bit FFT with 4-tap FIR)
  // also assumes only one change per cycle
  val coeffs_subset = coeffs_array.grouped(p).map(x=>x.head.head).toSeq
  require(coeffs_subset.length == taps)
  val coeffs = Vec( coeffs_subset.map(x => {
    val xT: T = Integral[T].fromInt(x)
    val xReg: T = Reg(t = null.asInstanceOf[T], next = null.asInstanceOf[T], init = xT.cloneType)
    xReg
  }) )
  val change = Vec.fill(taps) { Wire(Integral[T].fromInt(-1).cloneType) }
  change.foreach {x => x := Integral[T].zero }

  val coeffs_changes = scala.io.Source.fromFile("pfbcoeff_changes.csv").getLines.toSeq.map(_.split(",").map(_.toInt))
  val coeffs_changes_vec = Vec.fill(taps) { Vec.fill(n/p) { UInt(width=log2Up(p + 1)) } }
  val coeffs_sign_neg = Vec.fill(taps) { Vec.fill(n/p) { Bool() } }
  for (i <- 0 until coeffs_changes.length) {
    for (j <- 0 until coeffs_changes(i).length) {
      coeffs_changes_vec(i)(j) := UInt(scala.math.abs(coeffs_changes(i)(j)))
      coeffs_sign_neg(i)(j) := Bool(coeffs_changes(i)(j) < 0)
    }
  }
  for (i <- 0 until taps) {
    when (coeffs_changes_vec(i)(sync_in_repl) != UInt(0, log2Up(p + 1))) {
      when (coeffs_sign_neg(i)(sync_in_repl)) {
        change(i) := -Integral[T].one
      } .otherwise {
        change(i) := Integral[T].one
      }
    }
  }
  //val coeff_next = Vec( (0 until taps).map(x => (coeffs(x) + change(x))(cw-1,0)) ) // will not overflow by design
  val coeff_next = Vec( (coeffs, change).zipped map (_ + _) )
  coeffs := coeff_next
  // synchronize means no need for radiation hardening
  when (sync_in_repl === UInt(n/p-1)) {
    coeffs := Vec(coeffs_subset map (Integral[T].fromInt(_)))
  }
  // main FIR filter starts here
  // multiply, delay, and add
  val shift_out = Vec.fill(p) { Vec.fill(taps) { genIn } }
  val mult_out = Vec.fill(p) { Vec.fill(taps) { genOut.getOrElse(genIn) } }
  val add_out = Vec.fill(p) { Vec.fill(taps-1) { genOut.getOrElse(genIn) } }
  val overflow = Vec.fill(p) { Vec.fill(taps-1) { Bool() } }
  io.overflow := overflow.foldRight(Bool(false))((b,a) => a || b.foldRight(Bool(false))((b,a) => a || b))

  // split PFB into sub-banks, and share memory for each sub-bank
  val subbanks = math.min(8, p)
  for (h <- 0 until subbanks) {

    // use one big memory for every sub-bank
    if (n/p >= min_mem_depth) {
      val shift_out_temp = ShiftRegisterMem(Vec( (h*p/subbanks until (h + 1)*p/subbanks).flatMap(x => (0 until taps-1).map(y => shift_out(x)(y))) ), n/p, Bool(true), use_sp_mem, name="PFBMem_" + h)
      for (i <- h*p/subbanks until (h + 1)*p/subbanks) {
        for (j <- 0 until taps-1) {
          shift_out(i)(j + 1) := shift_out_temp((i-h*p/subbanks)*(taps-1) + j)
        }
      }
    }
    else {
      val shift_out_temp = ShiftRegister(Vec( (h*p/subbanks until (h + 1)*p/subbanks).flatMap(x => (0 until taps-1).map(y => shift_out(x)(y))) ), n/p)
      for (i <- h*p/subbanks until (h + 1)*p/subbanks) {
        for (j <- 0 until taps-1) {
          shift_out(i)(j + 1) := shift_out_temp((i-h*p/subbanks)*(taps-1) + j)
        }
      }
    }
  }

  for (i <- 0 until p) {

    shift_out(i)(0) := io.data_in(i)

    for (j <- 0 until taps) {

      // FIR filter
      if (changes) {
        when (UInt(i + 1, log2Up(p + 1)) >= coeffs_changes_vec(taps-j-1)(io.sync_in)) {
          //TODO truncate
          mult_out(i)(j) := coeff_next(taps-j-1) * shift_out(i)(j)
        } .otherwise {
          //TODO truncate
          mult_out(i)(j) := coeffs(taps-j-1) * shift_out(i)(j)
        }
      } else {
        if (symm && j >= taps/2) {
          //TODO truncate
          mult_out(i)(j) := (coeffs_vec(p-i-1 + j*p)(UInt(n/p-1)-io.sync_in) * shift_out(i)(j))
        } else {
          mult_out(i)(j) := (coeffs_vec(i + (taps-j-1)*p)(io.sync_in) * shift_out(i)(j))
        }
      }
      if (j == 1) {
        val add = mult_out(i)(j-1) + mult_out(i)(j)
        val sign_in1: Bool = mult_out(i)(j-1).isSignNegative() //Order[T].lt(mult_out(i)(j-1), Ring[T].zero)
        val sign_in2: Bool = mult_out(i)(j-1).isSignNegative() //Order[T].lt(mult_out(i)(j-1), Ring[T].zero)
        val sign_out: Bool = add.isSignNegative() //Order[T].lt(add, Ring[T].zero)
        overflow(i)(j-1) := ((sign_in1 && sign_in2) && !sign_out) || ((!sign_in1 && !sign_in2) && sign_out)
        add_out(i)(j-1) := add
        //add_out(i)(j-1) := mult_out(i)(j-1) + mult_out(i)(j)
      }
      else if (j != 0) {
        val add = add_out(i)(j-2) + mult_out(i)(j)
        val sign_in1 = mult_out(i)(j-1).isSignNegative()
        val sign_in2 = mult_out(i)(j).isSignNegative()
        val sign_out = add.isSignNegative()
        overflow(i)(j-1) := ((sign_in1 && sign_in2) && !sign_out) || ((!sign_in1 && !sign_in2) && sign_out)
        add_out(i)(j-1) := add
      }
    }

    // output
    if (pipe > 0) {
      io.data_out(i) := ShiftRegister(add_out(i)(taps-2), pipe)
    }
    else {
      io.data_out(i) := add_out(i)(taps-2)
    }
  }
}
