package ofdm

import chisel3._
import chisel3.util.Valid
import dsptools.numbers._
import dsptools.numbers.implicits._
import ieee80211.IEEE80211

class FIRIO[T <: Data](protoIn: T, protoOut: T) extends Bundle {
  val in  = Input(Valid(protoIn.cloneType))
  val out = Output(Valid(protoOut.cloneType))
}

class FIR[T <: Data : Ring](protoIn: T, protoOut: T, taps: Seq[T], zero: T) extends Module {
  val io = IO(new FIRIO(protoIn, protoOut))


  val prods = taps.map { t => t * io.in.bits }

  val regs = Seq.fill(taps.length - 1) { Reg(protoOut) }

  when (io.in.valid) {
    for (i <- 0 until regs.length - 1) {
      regs(i) := prods(i + 1) + regs(i + 1)
    }
    regs.last := prods.last
  }

  io.out.bits := prods.head + regs.head
  io.out.valid := io.in.valid
}

class STF64MatchedFilter[T <: Data : Ring : ConvertableTo](protoIn: T, protoOut: T, protoCoeff: T) extends FIR(
  protoIn=DspComplex(protoIn, protoIn),
  protoOut=DspComplex(protoOut, protoOut),
  taps=IEEE80211.stf64.toArray.map( x => DspComplex(
    implicitly[ConvertableTo[T]].fromDouble(x.real, protoCoeff),
    implicitly[ConvertableTo[T]].fromDouble(-x.imag, protoCoeff)
  )).reverse,
    DspComplex(implicitly[ConvertableTo[T]].fromDouble(0, protoCoeff), implicitly[ConvertableTo[T]].fromDouble(0, protoCoeff))
)

class STF16MatchedFilter[T <: Data : Ring : ConvertableTo](protoIn: T, protoOut: T, protoCoeff: T) extends FIR(
  protoIn=DspComplex(protoIn, protoIn),
  protoOut=DspComplex(protoOut, protoOut),
  taps=IEEE80211.stf64.slice(0,16).toArray.toSeq.map( x => DspComplex(
    implicitly[ConvertableTo[T]].fromDouble( x.real, protoCoeff),
    implicitly[ConvertableTo[T]].fromDouble(0.0-x.imag, protoCoeff)
  )).reverse,
  DspComplex(implicitly[ConvertableTo[T]].fromDouble(0, protoCoeff), implicitly[ConvertableTo[T]].fromDouble(0, protoCoeff))
)