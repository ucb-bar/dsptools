// See LICENSE for license details.

package dspblocks

import chisel3._
import chisel3.experimental.dontTouch
import chisel3.util.{HasBlackBoxResource, _}
import freechips.rocketchip.amba.apb._
import freechips.rocketchip.amba.ahb._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
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
  extends DspBlock[D, U, EO, EI, B] with HasCSR with HasIPXactParameters {

  addStatus(PassthroughDepth)

  val streamNode = AXI4StreamIdentityNode()

  lazy val module = new PassthroughModule(this)

  override def ipxactParameters: collection.Map[String, String] = Map(
    "maxDepth" -> params.depth.toString
  )
}

class PassthroughModule(val outer: Passthrough[_, _, _, _, _ <: Data]) extends LazyModuleImp(outer) {
  import outer.status


  val (in, _) = outer.streamNode.in.unzip
  val (out, _) = outer.streamNode.out.unzip
  //val mem = outer.mem.map(_.in.map(_._1))

  status(PassthroughDepth.name) := outer.params.depth.U

  out.head <> Queue(in.head, outer.params.depth)
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
    with APBDspBlockWithBus with APBHasCSR {
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
}

case object ByteRotateAmount extends CSRField {
  val name = "byteRotationAmount"
}

abstract class ByteRotate[D, U, EO, EI, B <: Data]()(implicit p: Parameters) extends DspBlock[D, U, EO, EI, B] with HasCSR {
  addControl(ByteRotateAmount)

  val streamNode = AXI4StreamIdentityNode()

  lazy val module = new ByteRotateModule(this)

}

class ByteRotateModule(val outer: ByteRotate[_, _, _, _, _ <: Data]) extends LazyModuleImp(outer) {
  import outer.control

  val (in, _)  = outer.streamNode.in.unzip
  val (out, _) = outer.streamNode.out.unzip
  val n = in.head.bits.params.n

  def rotateBytes(u: UInt, n: Int, rot: Int): UInt = {
    Cat(u(8*rot-1, 0), u(8*n-1, 8*rot))
  }

  out.head.valid := in.head.valid
  in.head.ready  := out.head.ready
  out.head.bits  := in.head.bits

  for (i <- 1 until n) {
    when (control(ByteRotateAmount.name) === i.U) {
      out.head.bits.data := rotateBytes(in.head.bits.data, n, i)
    }
  }
}

class APBByteRotate()(implicit  p: Parameters) extends
  ByteRotate[APBMasterPortParameters, APBSlavePortParameters, APBEdgeParameters, APBEdgeParameters, APBBundle]()(p)
  with APBHasCSR with APBDspBlockWithBus {
  override val csrBase   = 0
  override val csrSize   = 32
  override val beatBytes = 8

  makeCSRs()
}

class AHBByteRotate()(implicit  p: Parameters) extends
  ByteRotate[AHBMasterPortParameters, AHBSlavePortParameters, AHBEdgeParameters, AHBEdgeParameters, AHBBundle]()(p)
  with AHBHasCSR with AHBDspBlockWithBus {
  override val csrBase   = 0
  override val csrSize   = 32
  override val beatBytes = 8

  makeCSRs()
}

class AXI4ByteRotate()(implicit  p: Parameters) extends
  ByteRotate[AXI4MasterPortParameters, AXI4SlavePortParameters, AXI4EdgeParameters, AXI4EdgeParameters, AXI4Bundle]()(p)
  with AXI4HasCSR with AXI4DspBlock {
  override val csrBase   = 0
  override val csrSize   = 32
  override val beatBytes = 8
  val csrAddress = AddressSet(0x0, 0xff)

  makeCSRs()
}

class TLByteRotate()(implicit  p: Parameters) extends
  ByteRotate[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle]()(p)
  with TLHasCSR with TLDspBlock {
  override val csrBase   = BigInt(0)
  override val csrSize   = BigInt(32)
  override val beatBytes = 8
  val csrAddress = AddressSet(0x0, 0xff)

  makeCSRs()
}
