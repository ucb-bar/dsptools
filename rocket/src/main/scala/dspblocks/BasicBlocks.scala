// See LICENSE for license details.

package dspblocks

import chisel3._
import chisel3.util._
import freechips.rocketchip.amba.ahb._
import freechips.rocketchip.amba.apb._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.RegField
import freechips.rocketchip.tilelink._

import scala.language.existentials

trait AHBSlaveBasicBlock extends AHBSlaveDspBlock with AHBSlaveHasCSR {
  val csrAddress = AddressSet(0x0, 0xff)
  val beatBytes = 8
  override val mem = Some(AHBRegisterNode(address = csrAddress, beatBytes = beatBytes))
}

trait APBBasicBlock extends APBDspBlock with APBHasCSR {
  def csrAddress = AddressSet(0x0, 0xff)
  def beatBytes = 8
  override val mem = Some(APBRegisterNode(address = csrAddress, beatBytes = beatBytes))
}

trait AXI4BasicBlock extends AXI4DspBlock with AXI4HasCSR {
  def beatBytes = 8
  def csrAddress = AddressSet(0x0, 0xff)
  override val mem = Some(AXI4RegisterNode(address = csrAddress, beatBytes=beatBytes))
}

trait TLBasicBlock extends TLDspBlock with TLHasCSR {
  def csrAddress = AddressSet(0x0, 0xff)
  def beatBytes = 8
  def devname = "tlpassthrough"
  def devcompat = Seq("ucb-art", "dsptools")
  val device = new SimpleDevice(devname, devcompat) {
    override def describe(resources: ResourceBindings): Description = {
      val Description(name, mapping) = super.describe(resources)
      Description(name, mapping)
    }
  }
  override val mem = Some(TLRegisterNode(address = Seq(csrAddress), device = device, beatBytes = beatBytes))
}

case class PassthroughParams
(
  depth: Int = 0
) {
  require(depth >= 0, "Passthrough delay must be non-negative")
}

case object PassthroughDepth extends CSRField {
  override val name = "depth"
}

abstract class Passthrough[D, U, EO, EI, B <: Data](val params: PassthroughParams)(implicit p: Parameters)
  extends DspBlock[D, U, EO, EI, B] with HasCSR {
  val streamNode = AXI4StreamIdentityNode()

  lazy val module = new LazyModuleImp(this) {
    val (in, _) = streamNode.in.unzip
    val (out, _) = streamNode.out.unzip

    regmap(0x0 -> Seq(RegField.r(64, params.depth.U)))

    out.head <> Queue(in.head, params.depth)
  }
}

class AHBPassthrough(params: PassthroughParams)(implicit p: Parameters)
  extends Passthrough[AHBMasterPortParameters, AHBSlavePortParameters, AHBEdgeParameters, AHBEdgeParameters,
    AHBSlaveBundle](params) with AHBSlaveBasicBlock

class APBPassthrough(params: PassthroughParams)(implicit p: Parameters)
  extends Passthrough[APBMasterPortParameters, APBSlavePortParameters, APBEdgeParameters, APBEdgeParameters,
    APBBundle](params) with APBBasicBlock

class AXI4Passthrough(params: PassthroughParams)(implicit p: Parameters)
  extends Passthrough[AXI4MasterPortParameters, AXI4SlavePortParameters, AXI4EdgeParameters, AXI4EdgeParameters,
    AXI4Bundle](params) with AXI4BasicBlock

class TLPassthrough(params: PassthroughParams)(implicit p: Parameters)
  extends Passthrough[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle](params)
    with TLBasicBlock

case object ByteRotateAmount extends CSRField {
  override val name = "byteRotationAmount"
}

abstract class ByteRotate[D, U, EO, EI, B <: Data]()(implicit p: Parameters) extends DspBlock[D, U, EO, EI, B] with HasCSR {
  val streamNode = AXI4StreamIdentityNode()

  lazy val module = new LazyModuleImp(this) {
    val (in, _)  = streamNode.in.unzip
    val (out, _) = streamNode.out.unzip
    val n = in.head.bits.params.n
    val nWidth = log2Ceil(n) + 1

    val byteRotate = RegInit(0.U(nWidth.W))

    def rotateBytes(u: UInt, n: Int, rot: Int): UInt = {
      Cat(u(8*rot-1, 0), u(8*n-1, 8*rot))
    }

    out.head.valid := in.head.valid
    in.head.ready  := out.head.ready
    out.head.bits  := in.head.bits

    for (i <- 1 until n) {
      when (byteRotate === i.U) {
        out.head.bits.data := rotateBytes(in.head.bits.data, n, i)
      }
    }

    regmap(
      0x0 -> Seq(RegField(1 << log2Ceil(nWidth), byteRotate))
    )
  }
}

class AHBByteRotate()(implicit  p: Parameters) extends
  ByteRotate[AHBMasterPortParameters, AHBSlavePortParameters, AHBEdgeParameters, AHBEdgeParameters, AHBSlaveBundle]()(p)
  with AHBSlaveBasicBlock

class APBByteRotate()(implicit  p: Parameters) extends
  ByteRotate[APBMasterPortParameters, APBSlavePortParameters, APBEdgeParameters, APBEdgeParameters, APBBundle]()(p)
  with APBBasicBlock

class AXI4ByteRotate()(implicit  p: Parameters) extends
  ByteRotate[AXI4MasterPortParameters, AXI4SlavePortParameters, AXI4EdgeParameters, AXI4EdgeParameters, AXI4Bundle]()(p)
  with AXI4BasicBlock

class TLByteRotate()(implicit  p: Parameters) extends
  ByteRotate[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle]()(p)
  with TLBasicBlock
