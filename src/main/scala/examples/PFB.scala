// See LICENSE for license details.

// Author: Stevo Bailey (stevo.bailey@berkeley.edu)

package dsptools.examples

import chisel3.util.{Counter, ShiftRegister, log2Up}
import chisel3.{Bool, Bundle, Data, Module, Reg, UInt, Vec, Wire, when}
import dsptools.numbers.Real
import dsptools.numbers.implicits._

// polyphase filter bank io
class PFBIO[T<:Data](genIn: => T, genOut: => Option[T] = None,
                     windowSize: Int, parallelism: Int) extends Bundle {
  val data_in = Vec(parallelism, genIn.asInput)
  val data_out = Vec(parallelism, genOut.getOrElse(genIn).asOutput)
  val sync_in = UInt(log2Up(windowSize/parallelism))
  val overflow = Bool()
}

object sincHamming {
  def apply(size: Int, nfft: Int): Seq[Double] = Seq.tabulate(size) (i=>{
    val term1 = 0.54 - 0.46 * breeze.numerics.cos(2*scala.math.Pi * i.toDouble / size)
    val term2 = breeze.numerics.sinc(size.toDouble / nfft -0.5 * (size.toDouble / nfft) )
    println(term1 * term2 * 1024)
    term1 * term2 //* 2*512
  })
}

case class PFBConfig(
                      window: Seq[Double] = sincHamming(64, 64 / 8),
                      //windowSize: Int = 64,
                      outputWindowSize: Int = 16,
                      parallelism: Int = 8,
                      pipelineDepth: Int = 4,
                      useSinglePortMem: Boolean = false,
                      symmetricCoeffs: Boolean  = false,
                      useDeltaCompression: Boolean = false
                    ) {
  val windowSize = window.length

  // various checks for validity
  assert(window.length > 0)
  assert(window.length % outputWindowSize == 0, "Window size not a multiple of output window size")
  assert(outputWindowSize % parallelism == 0, "Output window size is not a multiple of parallelism")
}

class PFBnew[T<:Data:Real](
                            genIn: => T,
                            genOut: => Option[T] = None,
                            val config: PFBConfig = PFBConfig()
                          ) extends Module {
  val io = new PFBIO(genIn, genOut, config.windowSize, config.parallelism)

  val firGroup = Counter(config.outputWindowSize / config.parallelism)
  firGroup.inc()
  val firGroupPrev = ShiftRegister(firGroup.value, 1)

  val firs = (0 until config.outputWindowSize).map(idx => {
    val taps = config.window.zipWithIndex.filter {case (n, i) => i % config.outputWindowSize == idx} map ({case(n,i) => n})

    implicit def ev(x:Double) = fromDouble[T](x)
    val fir = Module(new ConstantTapTransposedStreamingFIR(genIn, genOut getOrElse(genIn), taps))
    fir
  })

  firs.zipWithIndex.map({case(f, i) => {
    val group = i % config.parallelism
    f.io.input.bits := io.data_in(group)
    f.io.input.valid := firGroup.value === UInt(group)
  }})

  io.data_out.zipWithIndex.map({case(p, i) => {
    val toMux = firs.zipWithIndex.filter({case(f, f_i) => f_i % config.parallelism == i}).map({case (f,_)=>f})
    val muxed = Vec(toMux.map(_.io.output.bits))
    p := muxed(firGroupPrev)
    toMux.zipWithIndex.map({case(f, f_i) => {
      chisel3.assert(f.io.output.valid ||  (UInt(f_i) != firGroupPrev) )
      chisel3.assert(!f.io.output.valid || (UInt(f_i) === firGroupPrev) )
    }})
  }})

 /* val firs = io.data_in.zipWithIndex.map {case (in, idx) => {
    val taps = config.window.zipWithIndex.filter {case (n, i) => i % config.parallelism ==idx} map {case(n, i) => n}
    implicit def ev(x:Double) = fromDouble[T](x)
    val fir = Module(new ConstantTapTransposedStreamingFIR(genIn, genOut getOrElse(genIn), taps))
    fir
  }}

  io.data_in zip (io.data_out) zip(firs) map {case ((in, out), fir) => {
    fir.io.input := in
    out := fir.io.output
  }}*/

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
class PFB[T<:Data:Real](genIn: => T, genOut: => Option[T],
                        n: Int, p: Int, min_mem_depth: Int,
                        taps: Int, pipe: Int, use_sp_mem: Boolean,
                        symm: Boolean = false, changes: Boolean = true)  extends Module {
  val io = new PFBIO(genIn, genOut, n, p)

  val bp = n/p

  val coeffs_array = scala.io.Source.fromFile("pfbcoeff.csv").getLines.toSeq.map(_.split(",").map(_.toInt))
  val coeffs_vec = Vec( coeffs_array.map( line => Vec ( line.map ( num => {
    val w = Wire(genIn)
    w := implicitly[Real[T]].fromInt(num)
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
    val xT: T = implicitly[Real[T]].fromInt(x)
    val xReg: T = Wire(xT.cloneType) //t = null.asInstanceOf[T], next = null.asInstanceOf[T], init = xT.cloneType)
    xReg := xT
    xReg
  }) )
  val change = Vec.fill(taps) { Wire(implicitly[Real[T]].fromInt(-1).cloneType) }
  change.foreach {x => x := implicitly[Real[T]].zero }

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
        change(i) := -implicitly[Real[T]].one
      } .otherwise {
        change(i) := implicitly[Real[T]].one
      }
    }
  }
  //val coeff_next = Vec( (0 until taps).map(x => (coeffs(x) + change(x))(cw-1,0)) ) // will not overflow by design
  val coeff_next = Vec( (coeffs, change).zipped map (_ + _) )
  coeffs := coeff_next
  // synchronize means no need for radiation hardening
  when (sync_in_repl === UInt(n/p-1)) {
    coeffs := Vec(coeffs_subset map (implicitly[Real[T]].fromInt(_)))
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
