package ofdm

import chisel3._
import chisel3.util._
import dsptools.numbers._
import dsptools.numbers.implicits._

case class PeakDetectParams[T]
(
  protoCorr: T,
  protoEnergyFF: T,
  protoEnergyMult: T,
  windowSize: Int,
  protoRaw:  Option[T] = None
) {
  val getProtoRaw = protoRaw.getOrElse(protoCorr)
}

class SampleAndCorr[T <: Data : Real](protoCorr: T, protoRaw: T) extends Bundle {
  val corr = Output(DspComplex(protoCorr.cloneType, protoCorr.cloneType))
  val raw  = Output(DspComplex(protoRaw.cloneType, protoRaw.cloneType))
  override def cloneType = new SampleAndCorr[T](protoCorr, protoRaw).asInstanceOf[this.type]
}

class PeakDetectIO[T <: Data : Real](p: PeakDetectParams[T]) extends Bundle {
  val in  = Input( Valid(new SampleAndCorr(p.protoCorr, p.getProtoRaw)))
  val out = Output(Valid(new SampleAndCorr(p.protoCorr, p.getProtoRaw)))
  val outLast = Output(Bool())

  val energyFF = Input(p.protoEnergyFF.cloneType)
  val energyMult = Input(p.protoEnergyMult.cloneType)
}

class PeakDetect[T <: Data : Real](p: PeakDetectParams[T]) extends Module {
  val io = IO(new PeakDetectIO(p))

  val accumEnergy      = RegInit(p.protoEnergyMult, Real[T].zero)
  val instEnergy       = io.in.bits.corr.abssq()
  val hasMaxInShr      = RegInit(false.B)
  val currentMax       = RegInit(0.U.asTypeOf(instEnergy.cloneType))
  val consecutiveMaxes = RegInit(0.U(log2Ceil(p.windowSize + 1).W))

  val shr = ShiftRegister(io.in.bits, p.windowSize + 1, 0.U.asTypeOf(io.in.bits.cloneType), io.in.fire())

  io.out.bits := shr
  io.out.valid := io.in.fire()
  io.outLast := false.B

  val hasBigEnergy = instEnergy * io.energyMult > accumEnergy
  val hasNewMax    = (!hasMaxInShr) || (instEnergy > currentMax)


  printf("hasMaxInShr %d\n\n", hasMaxInShr)
  when (io.in.fire()) {
    accumEnergy := instEnergy + (accumEnergy * io.energyFF)

    when (hasMaxInShr) {
      when (hasNewMax) {
        consecutiveMaxes := 0.U
      }   .otherwise {
        consecutiveMaxes := consecutiveMaxes + 1.U
      }
      when (consecutiveMaxes === p.windowSize.U) {
        io.outLast := true.B
        hasMaxInShr := false.B
        consecutiveMaxes := 0.U
        currentMax := instEnergy
      }
    }
    when (hasBigEnergy && hasNewMax) {
      hasMaxInShr := true.B
      consecutiveMaxes := 0.U
      currentMax := instEnergy
    }
  }
}
