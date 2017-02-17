// See LICENSE for license details.

package dspblocks

import cde._
import chisel3._
import chisel3.util.ShiftRegister

case object PassthroughDelay extends Field[Int]

trait HasPassthroughParameters {
  val p: Parameters

  def passthroughDelay = p(PassthroughDelay)
}

class Passthrough()(implicit p: Parameters) extends DspBlock()(p) {
  lazy val module = new PassthroughModule(this)

  addStatus("delay")
}

class PassthroughModule(outer: Passthrough)(implicit p: Parameters) extends DspBlockModule(outer)(p) with HasPassthroughParameters {
  val zeroBundle     = Wire(io.in.cloneType)
  zeroBundle.valid  := false.B
  zeroBundle.sync   := false.B
  zeroBundle.bits   := 0.U
  status("delay")   := passthroughDelay.U
  io.out            := ShiftRegister(io.in, passthroughDelay, zeroBundle, true.B)
}

class BarrelShifter()(implicit p: Parameters) extends DspBlock()(p) {
  lazy val module = new BarrelShifterModule(this)

  addControl("shiftBy", 0.U)
}

class BarrelShifterModule(outer: BarrelShifter)(implicit p: Parameters) extends DspBlockModule(outer)(p) {
  require( inputWidth == outputWidth )

  val shiftWidth = util.log2Up(inputWidth)

  val shiftByInRange = control("shiftBy") < inputWidth.U
  val shiftBy        = Mux(shiftByInRange, control("shiftBy"), 0.U)(shiftWidth - 1, 0)
  val shiftDown      = Mux(shiftBy === 0.U, 0.U, inputWidth.U - shiftBy)
  val shifted        = Mux(shiftBy === 0.U,
                           io.in.bits,
                           (io.in.bits << shiftBy) | (io.in.bits >> shiftDown)
                           )

  io.out.bits  := shifted
  io.out.valid := io.in.valid
  io.out.sync  := io.in.sync
}
