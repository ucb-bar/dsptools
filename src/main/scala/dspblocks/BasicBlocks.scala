// See LICENSE for license details.

package dspblocks

import chisel3._
import chisel3.util._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream.AXI4StreamIdentityNode
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

import scala.language.existentials

case class PassthroughParams
(
  depth: Int = 0
) {
  require(depth >= 0, "Passthrough delay must be non-negative")
}

case object PassthroughDepth extends CSRField {
  val name = "depth"
}

abstract class Passthrough[D, U, EO, EI, B <: Data](val params: PassthroughParams)(implicit p: Parameters)
  extends DspBlock[D, U, EO, EI, B] with HasCSR {

  addStatus(PassthroughDepth)

  val streamNode = AXI4StreamIdentityNode()

  lazy val module = new PassthroughModule(this)
}

class PassthroughModule(val outer: Passthrough[_, _, _, _, _ <: Data]) extends LazyModuleImp(outer) {
  import outer.status


  val (in, _) = outer.streamNode.in.unzip
  val (out, _) = outer.streamNode.out.unzip
  val mem = outer.mem.map(_.in.map(_._1))


  status(PassthroughDepth.name) := outer.params.depth.U

  out(0) <> Queue(in(0), outer.params.depth)
}

class AXI4Passthrough(params: PassthroughParams)(implicit p: Parameters)
  extends Passthrough[AXI4MasterPortParameters, AXI4SlavePortParameters, AXI4EdgeParameters, AXI4EdgeParameters, AXI4Bundle](params)
    with AXI4DspBlock with AXI4HasCSR {
  val csrSize = 32

  override def csrBase = 0

  override def beatBytes = 8

  override def csrAddress = AddressSet(0x0, 0xff)

  makeCSRs()

}

class TLPassthrough(params: PassthroughParams)(implicit p: Parameters)
  extends Passthrough[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle](params)
    with TLDspBlock with TLHasCSR {
  override val csrBase   = BigInt(0)
  override val csrSize   = BigInt(32)
  override val beatBytes = 8

  makeCSRs()
}
// case object PassthroughDelay extends Field[Int]
// 
// trait HasPassthroughParameters {
//   val p: Parameters
// 
//   def passthroughDelay = p(PassthroughDelay)
// }
// 
// class Passthrough()(implicit p: Parameters) extends DspBlock()(p) {
//   lazy val module = new PassthroughModule(this)
// 
//   addStatus("delay")
// }
// 
// class PassthroughModule(outer: Passthrough)(implicit p: Parameters) extends DspBlockModule(outer)(p) with HasPassthroughParameters {
//   val zeroBundle     = Wire(io.in.cloneType)
//   zeroBundle.valid  := false.B
//   zeroBundle.sync   := false.B
//   zeroBundle.bits   := 0.U
//   status("delay")   := passthroughDelay.U
//   io.out            := ShiftRegister(io.in, passthroughDelay, zeroBundle, true.B)
// }
// 
// class BarrelShifter()(implicit p: Parameters) extends DspBlock()(p) {
//   lazy val module = new BarrelShifterModule(this)
// 
//   addControl("shiftBy", 0.U)
// }
// 
// class BarrelShifterModule(outer: BarrelShifter)(implicit p: Parameters) extends DspBlockModule(outer)(p) {
//   require( inputWidth == outputWidth )
// 
//   val shiftWidth = util.log2Ceil(inputWidth)
// 
//   val shiftByInRange = control("shiftBy") < inputWidth.U
//   val shiftBy        = Mux(shiftByInRange, control("shiftBy"), 0.U)(shiftWidth - 1, 0)
//   val shiftDown      = Mux(shiftBy === 0.U, 0.U, inputWidth.U - shiftBy)
//   val shifted        = Mux(shiftBy === 0.U,
//                            io.in.bits,
//                            (io.in.bits << shiftBy) | (io.in.bits >> shiftDown)
//                            )
// 
//   io.out.bits  := shifted
//   io.out.valid := io.in.valid
//   io.out.sync  := io.in.sync
// }
