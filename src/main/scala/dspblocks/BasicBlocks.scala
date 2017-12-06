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
  val n = in(0).bits.params.n

  def rotateBytes(u: UInt, n: Int, rot: Int): UInt = {
    Cat(u(8*rot-1, 0), u(8*n-1, 8*rot))
  }

  out(0).valid := in(0).valid
  in(0).ready  := out(0).ready
  out(0).bits  := in(0).bits

  for (i <- 1 until n) {
    when (control(ByteRotateAmount.name) === i.U) {
      out(0).bits.data := rotateBytes(in(0).bits.data, n, i)
    }
  }
}

class APBByteRotate()(implicit  p: Parameters) extends
  ByteRotate[APBMasterPortParameters, APBSlavePortParameters, APBEdgeParameters, APBEdgeParameters, APBBundle]()(p)
  with APBHasCSR with APBDspBlock {
  override val csrBase   = 0
  override val csrSize   = 32
  override val beatBytes = 8

  makeCSRs()
}

class AHBByteRotate()(implicit  p: Parameters) extends
  ByteRotate[AHBMasterPortParameters, AHBSlavePortParameters, AHBEdgeParameters, AHBEdgeParameters, AHBBundle]()(p)
  with AHBHasCSR with AHBDspBlock {
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
