package ofdm

import chisel3._
import chisel3.util.{RegEnable, ShiftRegister, Valid}
import dsptools.numbers._

case class SyncParams[T <: Data]
(
  protoIn: DspComplex[T],
  protoOut: DspComplex[T],
  filterProtos: Tuple3[T, T, T],
  protoCORDIC: T,
  protoAngle: T,
  autocorrParams: AutocorrParams[DspComplex[T]],
  peakDetectParams: PeakDetectParams[T],
  ncoParams: NCOParams[T]
)

class SyncIO[T <: Data](params: SyncParams[T]) extends Bundle {
  val in = Flipped(Valid(params.protoIn))
  val out = Valid(params.protoOut)

  val autocorrConfig   = new AutocorrConfigIO(params.autocorrParams)
  val peakDetectConfig = new PeakDetectConfigIO(params.peakDetectParams)
  val autocorrFF       = Input(params.protoAngle)
  val freqScaleFactor  = Input(params.protoAngle)
}

class Sync[T <: Data : Real : BinaryRepresentation](params: SyncParams[T])/*(implicit convertableTo: ConvertableTo[DspComplex[T]])*/ extends Module {
  val io = IO(new SyncIO(params))


  val autocorr = Module(new AutocorrSimple(params.autocorrParams))
  autocorr.io.config <> io.autocorrConfig

  val matchedFilter = Module(new STF64MatchedFilter[T](params.filterProtos._1, params.filterProtos._2, params.filterProtos._3))

  val peakDetect = Module(new PeakDetect(params.peakDetectParams))
  peakDetect.io.config <> io.peakDetectConfig

  val cordic = Module(IterativeCORDIC.circularVectoring(autocorr.io.out.bits.real.cloneType, params.protoAngle))

  autocorr.io.in := io.in
  matchedFilter.io.in := io.in

  val delay = params.autocorrParams.maxApart + cordic.io.out.bits.z.getWidth + 1
  val delayedIn = ShiftRegister(io.in.bits, n = delay, en = io.in.valid, resetData = 0.U.asTypeOf(io.in.bits))

  val sampleAndCorr: Valid[SampleAndCorr[T]] = {
    val wire = Wire(Valid(new SampleAndCorr[T](matchedFilter.io.out.bits.real, io.in.bits.real)))

    wire.valid := matchedFilter.io.out.valid
    wire.bits.corr := matchedFilter.io.out.bits
    wire.bits.raw := autocorr.io.out.bits

    // make an assertion about valid signals?

    wire
  }

  peakDetect.io.in := sampleAndCorr

  val peakDetectRawOut: Valid[DspComplex[T]] = {
    val wire = Wire(Valid(peakDetect.io.out.bits.raw))
    wire.valid := peakDetect.io.outLast //peakDetect.io.out.valid
    wire.bits  := peakDetect.io.out.bits.raw
    wire
  }

  val complexAutocorrFF = Wire(DspComplex(io.autocorrFF.cloneType, io.autocorrFF.cloneType))
  complexAutocorrFF.real := io.autocorrFF
  complexAutocorrFF.imag :=   0.U.asTypeOf(io.autocorrFF.cloneType)
  val peakDetectAverage: DspComplex[T] = Forgettable(peakDetectRawOut, complexAutocorrFF)

  cordic.io.in.valid  := RegNext(peakDetect.io.outLast, false.B)
  cordic.io.in.bits.x := peakDetectAverage.real
  cordic.io.in.bits.y := peakDetectAverage.imag
  cordic.io.in.bits.z := Ring[T].zero
  cordic.io.out.ready := true.B

  // assert(!cordic.io.in.valid || cordic.io.in.ready, "CORDIC should be ready when new signal arrives")

  val cordicOut = RegEnable(cordic.io.out.bits.z, init = Ring[T].zero, enable = cordic.io.out.fire())

  val nco = Module(new NCO(params.ncoParams))
  nco.io.en := io.in.valid
  nco.io.freq := cordicOut * io.freqScaleFactor

  io.out.bits := nco.io.out.bits * delayedIn
  io.out.valid := nco.io.out.valid

}
