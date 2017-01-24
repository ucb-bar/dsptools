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

class LazyPassthrough()(implicit p: Parameters) extends LazyDspBlock()(p) {
  lazy val module = new Passthrough(this)

  addStatus("delay")
}

class Passthrough(outer: LazyPassthrough)(implicit p: Parameters) extends DspBlock(outer)(p) with HasPassthroughParameters {
  status("delay") := passthroughDelay.U
  io.out          := ShiftRegister(io.in, passthroughDelay)
}

class LazyBarrelShifter()(implicit p: Parameters) extends LazyDspBlock()(p) {
  lazy val module = new BarrelShifter(this)

  addControl("shiftBy", 0.U)
}

class BarrelShifter(outer: LazyBarrelShifter)(implicit p: Parameters) extends DspBlock(outer)(p) {
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
