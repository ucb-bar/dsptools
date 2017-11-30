package ofdm

import chisel3._
import chisel3.core.requireIsChiselType
import chisel3.util._

class ShiftRegisterMem[T <: Data](val gen: T, val maxDepth: Int) extends Module {
  require(maxDepth > 1, s"Depth must be > 1, got $maxDepth")
  requireIsChiselType(gen)

  val io = IO(new Bundle {
    val depth = Input(Valid(UInt(log2Ceil(maxDepth + 1).W)))
    val in    = Input(Valid(gen.cloneType))
    val out   = Output(Valid(gen.cloneType))
  })

  val mem        = SyncReadMem(maxDepth, gen)
  val readIdx    = Wire(UInt(log2Ceil(maxDepth).W))
  val readIdxReg = RegInit(0.U(log2Ceil(maxDepth).W) - (maxDepth - 1).U)
  val writeIdx   = RegInit(0.U(log2Ceil(maxDepth).W))

  when (io.depth.valid) {
    val diff = writeIdx - io.depth.bits
    when (diff >= 0.U) {
      readIdx := diff
    }   .otherwise {
      readIdx := maxDepth.U - diff
    }
  }   .otherwise {
    readIdx := readIdxReg
  }

  when (io.in.valid) {
    readIdxReg := Mux(readIdx < (maxDepth - 1).U, readIdx + 1.U, 0.U)
    writeIdx := Mux(writeIdx < (maxDepth - 1).U, writeIdx + 1.U, 0.U)
  }   .otherwise {
    readIdxReg := readIdx
  }

  mem.write(writeIdx, io.in.bits)
  io.out.bits := mem.read(readIdx)
  io.out.valid := RegNext(io.in.fire(), init = false.B)
}

object ShiftRegisterMem {
  def apply[T <: Data](in: Valid[T], depth: Int): Valid[T] = {
    val depthW = Wire(Valid(UInt()))
    depthW.valid := RegNext(false.B, init=true.B)
    depthW.bits  := depth.U
    apply(in, depth, depthW)
  }

  def apply[T <: Data](in: Valid[T], maxDepth: Int, depth: Valid[UInt]): Valid[T] = {
    val shiftRegisterMem = Module(new ShiftRegisterMem(in.bits.cloneType, maxDepth))
    shiftRegisterMem.io.in := in
    shiftRegisterMem.io.depth := depth
    shiftRegisterMem.io.out
  }
}