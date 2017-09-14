package ofdm

import chisel3._
import chisel3.util.Valid
import dsptools.numbers.Real

case class CFOCorrectParams[T]
(
  protoCorr: T,
  protoRaw:  Option[T] = None
) {
  val getProtoRaw = protoRaw.getOrElse(protoCorr)
}

class CFOCorrectIO[T <: Data : Real](p: CFOCorrectParams[T]) extends Bundle {
  val in = Input(Valid(new SampleAndCorr(p.protoCorr.cloneType, p.getProtoRaw.cloneType)))
  val out = Output(Valid(p.getProtoRaw.cloneType))
}

class CFOCorrect[T <: Data : Real](p: CFOCorrectParams[T]) extends Module {
  val io = IO(new CFOCorrectIO(p))


}
