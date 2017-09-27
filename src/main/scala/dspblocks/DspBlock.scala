// See LICENSE for license details

package dspblocks

import chisel3._
import chisel3.internal.firrtl.Width
import freechips.rocketchip.amba.apb._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._

import scala.language.implicitConversions // for csrField conversion

sealed trait CSRType
case object CSRControl extends CSRType
case object CSRStatus extends CSRType

object CSR {
  //               dir           width  init
  type RegInfo = (CSRType, Width, BigInt)
  type Map     = scala.collection.Map[String, RegInfo]
}

trait CSRField {
  def name: String
}

trait HasCSR {
  implicit def csrFieldToString(in: CSRField): String = in.name
  val csrMap = scala.collection.mutable.Map[String, CSR.RegInfo]()


  def addStatus(name: String, init: BigInt = 0, width: Width = 64.W): Unit = {
    csrMap += (name -> (CSRStatus, width, init))
  }

  def addControl(name: String, init: BigInt = 0, width: Width = 64.W): Unit = {
    csrMap += (name -> (CSRControl, width, init))
  }
}

trait DspBlock[D, U, EO, EI, B <: Data] {
  val streamNode: MixedNode[
    AXI4StreamMasterPortParameters,
    AXI4StreamSlavePortParameters,
    AXI4StreamEdgeParameters,
    AXI4StreamBundle,
    AXI4StreamMasterPortParameters,
    AXI4StreamSlavePortParameters,
    AXI4StreamEdgeParameters,
    AXI4StreamBundle]

  val mem: Option[MixedNode[D, U, EI, B, D, U, EO, B]]
}

case class DspBlockBlindNodes[D, U, EO, EI, B <: Data]
(
  val streamIn:  () => AXI4StreamBlindInputNode,
  val streamOut: () => AXI4StreamBlindOutputNode,
  val mem:       () => MixedNode[D, U, EI, B, D, U, EO, B]
)


object DspBlock {
  type AXI4BlindNodes = DspBlockBlindNodes[
    AXI4MasterPortParameters,
    AXI4SlavePortParameters,
    AXI4EdgeParameters,
    AXI4EdgeParameters,
    AXI4Bundle]

  def blindWrapper[D, U, EO, EI, B <: Data]
  (mod: () => DspBlock[D, U, EO, EI, B],
   blindParams: DspBlockBlindNodes[D, U, EO, EI, B])(implicit p: Parameters): DspBlock[D, U, EO, EI, B] = {
    class BlindWrapper extends LazyModule with DspBlock[D, U, EO, EI, B] {
      val streamIn  = blindParams.streamIn()
      val streamOut = blindParams.streamOut()
      val memNode   = blindParams.mem()
      val mem = Some(memNode)
      val streamNode = streamIn

      val internal = mod()

      internal.streamNode := streamIn
      streamOut      := internal.streamNode
      internal.mem.map { m => m := memNode }


      lazy val module = new LazyModuleImp(this) {
        val io = IO(new Bundle {
          val in = streamIn.bundleIn
          val out = streamOut.bundleOut
          val mem = memNode.bundleOut
        })
      }

    }

    LazyModule(new BlindWrapper)
  }
}

class CSRRecord(csrMap: CSR.Map) extends Record {
  val elements = new scala.collection.immutable.ListMap() ++
    csrMap map { case (name, (dir, width, _)) =>
      val gen = dir match {
        case CSRStatus  => Input(UInt(width))
        case CSRControl => Output(UInt(width))
      }
      name -> gen
    }
  def apply(field: CSRField) = elements(field.name)
  def apply(name: String) = elements(name)
  override def cloneType = new CSRRecord(csrMap).asInstanceOf[this.type]
}

trait CSRIO extends Bundle {
  val csrMap: CSR.Map
  lazy val csrs = new CSRRecord(csrMap)
}

trait CSRModule extends HasRegMap {
  val io: CSRIO
  val csrMap: CSR.Map

  val widthToRegMap = csrMap map { case (name, (dir, width, init)) =>
    val reg = RegInit(init.U(width))
    reg.suggestName(name)
    dir match {
      case CSRStatus => reg := io.csrs(name)
      case CSRControl => io.csrs(name) := reg
    }
    reg -> width
  }

  val addrs = widthToRegMap.scanLeft(0) { case (widthIn, (_, width)) =>
    val widthToBytes = (width.get + 7) / 8
    widthIn + widthToBytes
  }
  val mapping: Seq[RegField.Map] = widthToRegMap.zip(addrs).map { case ((reg, width), addr) =>
    val widthToBytes = (width.get + 7) / 8
    addr -> Seq(RegField(widthToBytes * 8, reg))
  }.toSeq

  val addrmap: Map[String, Int] = csrMap.zip(addrs).map { case ((name, _), addr) => name -> addr }.toMap

  def addrmap(field: CSRField): Int = addrmap(field.name)

  regmap(mapping: _*)
}

trait TLHasCSR extends HasCSR { this: TLDspBlock =>
  val csrBase: BigInt
  val csrSize: BigInt
  val csrDevname = "tlsram:reg"
  val csrDevcompat: Seq[String] = Seq()

  val beatBytes: Int

  def makeCSRs(dummy: Int = 0) = {
    require(mem.isDefined, "Need memory interface for CSR")

    val myMapping = csrMap
    val csrs = LazyModule(
      new TLRegisterRouter(csrBase, csrDevname, csrDevcompat, size=csrSize, beatBytes=beatBytes)(
      new TLRegBundle((), _) with CSRIO { lazy val csrMap = myMapping })(
      new TLRegModule((), _, _) with CSRModule { lazy val csrMap = myMapping }))

    csrs.node := mem.get
    csrs
  }
}

trait APBHasCSR extends HasCSR { this: APBDspBlock =>
  val csrBase: Int
  val csrSize: Int
  val beatBytes: Int

  def makeCSRs(dummy: Int = 0) = {
    require(mem.isDefined, "Need memory interface for CSR")

    val myMapping = csrMap
    val csrs = LazyModule(
      new APBRegisterRouter(csrBase, size = csrSize, beatBytes = beatBytes)(
      new APBRegBundle((), _) with CSRIO { lazy val csrMap = myMapping })(
      new APBRegModule((), _, _) with CSRModule { lazy val csrMap = myMapping }))
    csrs.node := mem.get
    csrs
  }
}

trait AXI4HasCSR extends HasCSR { this: AXI4DspBlock =>
  def csrBase: Int
  def csrSize: Int
  def beatBytes: Int
  def csrAddress: AddressSet

  val mem = Some(AXI4InputNode())

  lazy val csrs = {
    val myMapping = csrMap
    LazyModule(
      new AXI4RegisterRouter(csrBase, size = csrSize, beatBytes = beatBytes)(
        new AXI4RegBundle((), _) with CSRIO { lazy val csrMap = myMapping })(
        new AXI4RegModule((), _, _) with CSRModule { lazy val csrMap = myMapping }) {
        lazy val addrmap = module.addrmap
      })
  }

  def makeCSRs(dummy: Int = 0) = {
    csrs.node := mem.get
    csrs
  }
}

trait TLDspBlock extends DspBlock[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle] {
  implicit val p: Parameters
  val bus = LazyModule(new TLXbar)
  val mem = Some(bus.node)
}

trait APBDspBlock extends DspBlock[APBMasterPortParameters, APBSlavePortParameters, APBEdgeParameters, APBEdgeParameters, APBBundle] {
  implicit val p: Parameters
  val bus = LazyModule(new APBFanout)
  val mem = Some(bus.node)
}

trait AXI4DspBlock extends DspBlock[AXI4MasterPortParameters, AXI4SlavePortParameters, AXI4EdgeParameters, AXI4EdgeParameters, AXI4Bundle] {
  implicit val p: Parameters
  // don't define mem b/c we don't have a bus for axi4 yet
}
