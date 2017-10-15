package ofdm

import chisel3._
import chisel3.experimental._
import chisel3.util._
import dsptools.numbers._

case class NCOParams[T <: Data]
(
  phaseWidth: Int,
  tableSize: Int,
  phaseConv: UInt => T,
  protoFreq: T,
  protoOut: T,
  protoTable: Option[T] = None
) {
  requireIsChiselType(protoFreq)
  requireIsChiselType(protoOut)

  val getProtoTable = protoTable.getOrElse(protoOut)
  val tableParams = NCOTableParams(
    phaseWidth = phaseWidth,
    phaseConv = phaseConv,
    protoTable = getProtoTable,
    protoOut = protoOut,
    tableSize = tableSize
  )
}

class NCOIO[T <: Data : Real](params: NCOParams[T]) extends Bundle {
  val en = Input(Bool())
  val freq = Input(params.protoFreq)
  val out  = Output(Valid(DspComplex(params.protoOut, params.protoOut)))
}

class NCO[T <: Data : Real : BinaryRepresentation](params: NCOParams[T]) extends Module {
  val io = IO(new NCOIO(params))


  val phaseCounter = Reg(UInt(params.phaseWidth.W))
  val phaseConverter = Module(new NCOTable(params.tableParams))

  when (io.en) {
    phaseCounter := phaseCounter + io.freq.asUInt()
  }

  phaseConverter.io.phase := phaseCounter

  io.out.bits.real := phaseConverter.io.cosOut
  io.out.bits.imag := phaseConverter.io.sinOut
  io.out.valid     := RegNext(io.en, false.B)

}
