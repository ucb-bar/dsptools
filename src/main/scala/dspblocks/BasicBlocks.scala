// See LICENSE for license details.

package dspblocks

import chisel3._
import chisel3.experimental.dontTouch
import chisel3.util.{HasBlackBoxResource, _}
import freechips.rocketchip.amba.apb.{APBBundle, APBEdgeParameters, APBMasterPortParameters, APBSlavePortParameters}
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

class APBPassthrough(params: PassthroughParams)(implicit p: Parameters)
  extends Passthrough[APBMasterPortParameters, APBSlavePortParameters, APBEdgeParameters, APBEdgeParameters, APBBundle](params)
    with APBDspBlock with APBHasCSR {
  override val csrBase   = 0
  override val csrSize   = 32
  override val beatBytes = 8

  makeCSRs()
}

class TLPassthrough(params: PassthroughParams)(implicit p: Parameters)
  extends Passthrough[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle](params)
    with TLDspBlock with TLHasCSR {
  override val csrBase   = BigInt(0)
  override val csrSize   = BigInt(32)
  override val beatBytes = 8

  makeCSRs()
  class PlusargReaderHack extends BlackBox with HasBlackBoxResource {
    override def desiredName: String = "plusarg_reader"
    val io = IO(new Bundle {
      val out = Output(UInt(32.W))
    })

    setResource("/plusarg_reader.v")
  }
  override lazy val module = new PassthroughModule(this) {
    val hack = Module(new PlusargReaderHack)
    dontTouch(hack.io.out)
  }
}
