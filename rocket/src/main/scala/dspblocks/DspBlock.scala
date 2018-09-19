// See LICENSE for license details

package dspblocks

import amba.axi4stream.AXI4StreamNode
import chisel3._
import chisel3.internal.firrtl.Width
import freechips.rocketchip.amba.ahb._
import freechips.rocketchip.amba.apb._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._

import scala.collection.immutable
import scala.language.implicitConversions // for csrField conversion

sealed trait CSRType
case object CSRControl extends CSRType
case object CSRStatus extends CSRType

case class RegInfo(tpe: CSRType, width: Width, init: BigInt)

object CSR {
  type Map = scala.collection.Map[String, RegInfo]
}

trait CSRField {
  def name: String = this.getClass.getSimpleName
}

trait HasCSR {
  implicit def csrFieldToString(in: CSRField): String = in.name
  val csrMap = scala.collection.mutable.Map[String, RegInfo]()

  def addStatus(name: String, init: BigInt = 0, width: Width = 64.W): Unit = {
    csrMap += (name -> RegInfo(CSRStatus, width, init))
  }

  def addControl(name: String, init: BigInt = 0, width: Width = 64.W): Unit = {
    csrMap += (name -> RegInfo(CSRControl, width, init))
  }

  def status(name: String): UInt = {
    require(csrMap(name).tpe == CSRStatus, s"Register $name is not a status")
    getCSRByName(name)
  }

  def control(name: String): UInt = {
    require(csrMap(name).tpe == CSRControl, s"Register $name is not a status")
    getCSRByName(name)
  }
  protected def getCSRByName(name: String): UInt
}

trait DspBlock[D, U, EO, EI, B <: Data] extends LazyModule {
  val streamNode: AXI4StreamNode
  val mem: Option[MixedNode[D, U, EI, B, D, U, EO, B]]
  //val mem: Option[NodeHandle[D, U, EI, B, D, U, EO, B]]
}

trait StandaloneBlock[D, U, EO, EI, B <: Data] extends DspBlock[D, U, EO, EI, B] {
  val ioInNode = BundleBridgeSource(() => new AXI4StreamBundle(AXI4StreamBundleParameters(n = 8)))
  val ioOutNode = BundleBridgeSink[AXI4StreamBundle]()

  ioOutNode :=
    AXI4StreamToBundleBridge(AXI4StreamSlaveParameters()) :=
    streamNode :=
    BundleBridgeToAXI4Stream(AXI4StreamMasterParameters()) :=
    ioInNode

  val in = InModuleBody { ioInNode.makeIO() }
  val out = InModuleBody { ioOutNode.makeIO() }
}

trait AXI4StandaloneBlock extends StandaloneBlock[
  AXI4MasterPortParameters,
  AXI4SlavePortParameters,
  AXI4EdgeParameters,
  AXI4EdgeParameters,
  AXI4Bundle] {
  def standaloneParams = AXI4BundleParameters(addrBits = 64, dataBits = 64, idBits = 1)
  val ioMem = mem.map { m => {
    val ioMemNode = BundleBridgeSource(() => AXI4Bundle(standaloneParams))

    m :=
      BundleBridgeToAXI4(AXI4MasterPortParameters(Seq(AXI4MasterParameters("bundleBridgeToAXI4")))) :=
      ioMemNode

    val ioMem = InModuleBody { ioMemNode.makeIO() }
    ioMem
  }}
}

trait APBStandaloneBlock extends StandaloneBlock[
  APBMasterPortParameters,
  APBSlavePortParameters,
  APBEdgeParameters,
  APBEdgeParameters,
  APBBundle] {
  def standaloneParams = APBBundleParameters(addrBits = 64, dataBits = 64)
  val ioMem = mem.map { m => {
    val ioMemNode = BundleBridgeSource(() => APBBundle(standaloneParams))
    m :=
      BundleBridgeToAPB(APBMasterPortParameters(Seq(APBMasterParameters("bundleBridgeToAPB")))) :=
      ioMemNode

    val ioMem = InModuleBody { ioMemNode.makeIO() }
    ioMem
  }}
}

trait TLStandaloneBlock extends StandaloneBlock[
  TLClientPortParameters,
  TLManagerPortParameters,
  TLEdgeOut,
  TLEdgeIn,
  TLBundle] {
  def standaloneParams = TLBundleParameters(addressBits = 64, dataBits = 64, sourceBits = 1, sinkBits = 1, sizeBits = 6)
  val ioMem = mem.map { m => {
    val ioMemNode = BundleBridgeSource(() => TLBundle(standaloneParams))
    m :=
      BundleBridgeToTL(TLClientPortParameters(Seq(TLClientParameters("bundleBridgeToTL")))) :=
      ioMemNode
    val ioMem = InModuleBody { ioMemNode.makeIO() }
    ioMem
  }}
}

case class DspBlockBlindNodes[D, U, EO, EI, B <: Data]
(
  streamIn:  () => AXI4StreamMasterNode,
  streamOut: () => AXI4StreamSlaveNode,
  mem:       () => MixedNode[D, U, EI, B, D, U, EO, B]
)

object DspBlockBlindNodes {
  def apply[D, U, EO, EI, B <: Data]
  (
    streamParams: AXI4StreamBundleParameters = AXI4StreamBundleParameters(n=8),
    mem: () => MixedNode[D, U, EI, B, D, U, EO, B],
    name: String = ""
  )(implicit valName: ValName): DspBlockBlindNodes[D, U, EO, EI, B] = {
    DspBlockBlindNodes(
      streamIn = () => AXI4StreamMasterNode(Seq(AXI4StreamMasterPortParameters(
        Seq(AXI4StreamMasterParameters(
          n = streamParams.n,
          u = streamParams.u,
          numMasters = 1 << streamParams.i
        ))
      ))),
      streamOut = () => AXI4StreamSlaveNode(Seq(AXI4StreamSlavePortParameters(
        Seq(AXI4StreamSlaveParameters(
          numEndpoints = 1 << streamParams.d,
          hasData = streamParams.hasData,
          hasStrb = streamParams.hasStrb,
          hasKeep = streamParams.hasKeep
        ))
      ))),
      mem = mem
    )
  }
}

object DspBlock {
  type AXI4BlindNodes = DspBlockBlindNodes[
    AXI4MasterPortParameters,
    AXI4SlavePortParameters,
    AXI4EdgeParameters,
    AXI4EdgeParameters,
    AXI4Bundle]
}

class CSRRecord(csrMap: CSR.Map) extends Record {
  val elements: immutable.ListMap[String, UInt] = immutable.ListMap[String, UInt]() ++
    csrMap map { case (name, RegInfo(dir, width, _)) =>
      val gen: UInt = dir match {
        case CSRStatus  => Input(UInt(width))
        case CSRControl => Output(UInt(width))
      }
      name -> gen
    }
  def apply(field: CSRField): UInt = elements(field.name)
  def apply(name: String): UInt = elements(name)
  override def cloneType = new CSRRecord(csrMap).asInstanceOf[this.type]
}

trait CSRIO extends Bundle {
  val csrMap: CSR.Map
  lazy val csrs = new CSRRecord(csrMap)
}

trait CSRModule extends HasRegMap {
  val io: CSRIO
  val csrMap: CSR.Map

  val widthToRegMap = csrMap map { case (name, RegInfo(dir, width, init)) =>
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
  val mem = Some(TLIdentityNode())

  val csrBase: BigInt
  val csrSize: BigInt
  val csrDevname = "tlsram:reg"
  val csrDevcompat: Seq[String] = Seq()

  val beatBytes: Int

  lazy val csrs = {
    val myMapping = csrMap
    LazyModule(
      new TLRegisterRouter(csrBase, csrDevname, csrDevcompat, size=csrSize, beatBytes=beatBytes)(
        new TLRegBundle((), _) with CSRIO { lazy val csrMap = myMapping })(
        new TLRegModule((), _, _) with CSRModule { lazy val csrMap = myMapping }))
  }

  def makeCSRs(dummy: Int = 0) = {
    require(mem.isDefined, "Need memory interface for CSR")

    csrs.node := mem.get
    csrs
  }

  override def getCSRByName(name: String): UInt = csrs.module.io.csrs(name)
}

trait AHBHasCSR extends HasCSR { this: AHBDspBlock =>
  val csrBase: Int
  val csrSize: Int
  val beatBytes: Int

  protected def getCSRByName(name: String): UInt = {
    csrs.module.io.csrs(name)
  }

  lazy val csrs = {
    val myMapping = csrMap
    LazyModule(
      new AHBRegisterRouter(csrBase, size = csrSize, beatBytes = beatBytes)(
        new AHBRegBundle((), _) with CSRIO { lazy val csrMap = myMapping })(
        new AHBRegModule((), _, _) with CSRModule { lazy val csrMap = myMapping }))
  }

  def makeCSRs(dummy: Int = 0) = {
    require(mem.isDefined, "Need memory interface for CSR")

    csrs.node := mem.get
    csrs
  }
}

trait APBHasCSR extends HasCSR { this: APBDspBlock =>
  val csrBase: Int
  val csrSize: Int
  val beatBytes: Int

  protected def getCSRByName(name: String): UInt = {
    csrs.module.io.csrs(name)
  }

  lazy val csrs = {
    val myMapping = csrMap
    LazyModule(
      new APBRegisterRouter(csrBase, size = csrSize, beatBytes = beatBytes)(
        new APBRegBundle((), _) with CSRIO { lazy val csrMap = myMapping })(
        new APBRegModule((), _, _) with CSRModule { lazy val csrMap = myMapping }))
  }

  def makeCSRs(dummy: Int = 0) = {
    require(mem.isDefined, "Need memory interface for CSR")

    csrs.node := mem.get
    csrs
  }
}

trait AXI4HasCSR extends HasCSR { this: AXI4DspBlock =>
  def csrBase: Int
  def csrSize: Int
  def beatBytes: Int
  def csrAddress: AddressSet

  val mem = Some(AXI4IdentityNode())

  protected def getCSRByName(name: String): UInt = {
    csrs.module.io.csrs(name)
  }

  lazy val csrs = {
    val myMapping = csrMap
    val csrs = LazyModule(
      new AXI4RegisterRouter(csrBase, size = csrSize, beatBytes = beatBytes)(
        new AXI4RegBundle((), _) with CSRIO { lazy val csrMap = myMapping })(
        new AXI4RegModule((), _, _) with CSRModule { lazy val csrMap = myMapping }) {
        lazy val addrmap = module.addrmap
      })
    //mem.get := csrs.node
    //csrs.node := mem.get
    csrs
  }

  def makeCSRs(dummy: Int = 0) = {
    require(mem.isDefined, "Need memory interface for CSR")

    csrs.node := mem.get
    csrs
  }
}

trait HasBus[T <: LazyModule] {
  val bus: T
}

trait TLDspBlock extends DspBlock[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle]

trait TLDspBlockWithBus extends TLDspBlock with HasBus[TLXbar] {
  val bus = LazyModule(new TLXbar)
  val mem = Some(bus.node)
}

trait APBDspBlock extends DspBlock[APBMasterPortParameters, APBSlavePortParameters, APBEdgeParameters, APBEdgeParameters, APBBundle]

trait APBDspBlockWithBus extends APBDspBlock with HasBus[APBFanout] {
  val bus = LazyModule(new APBFanout)
  val mem = Some(bus.node)
}

object AXI4DspBlock {
  type AXI4Node = MixedNode[
    AXI4MasterPortParameters, AXI4SlavePortParameters, AXI4EdgeParameters, AXI4Bundle,
    AXI4MasterPortParameters, AXI4SlavePortParameters, AXI4EdgeParameters, AXI4Bundle
    ]
}

trait AXI4DspBlock extends DspBlock[AXI4MasterPortParameters, AXI4SlavePortParameters, AXI4EdgeParameters, AXI4EdgeParameters, AXI4Bundle]

trait AXI4DspBlockWithBus extends AXI4DspBlock with HasBus[AXI4Xbar] {
  val bus = LazyModule(new AXI4Xbar)
  val mem = Some(bus.node)
}

trait AHBDspBlock extends DspBlock[AHBMasterPortParameters, AHBSlavePortParameters, AHBEdgeParameters, AHBEdgeParameters, AHBBundle]

trait AHBDspBlockWithBus extends AHBDspBlock with HasBus[AHBFanout] {
  val bus = LazyModule(new AHBFanout)
  val mem = Some(bus.node)
}


trait HierarchicalDspBlock[ID, D, U, EO, EI, B <: Data] extends DspBlock[D, U, EO, EI, B] {
  type Block = DspBlock[D, U, EO, EI, B]
  def blocks: Map[ID, () => Block]
  def connections: Seq[(Block, Block)]
  def connect(lhs: Block, rhs: Block): Unit = {
    lhs.streamNode := rhs.streamNode
  }
}

case class PGLAHierarchicalDspBlockParameters[ID, D, U, EO, EI, B <: Data]
(
  logicAnalyzerSamples: Int = 128,
  logicAnalyzerUseCombinationalTrigger: Boolean = true,
  patternGeneratorSamples: Int = 128,
  patternGeneratorUseCombinationalTrigger: Boolean = true
)

abstract class PGLAHierarchicalDspBlock[ID, D, U, EO, EI, B <: Data]()(implicit p: Parameters) extends HierarchicalDspBlock[ID, D, U, EO, EI, B] {

  override def connect(lhs: Block, rhs: Block): Unit = {

  }
}


case class DspChainParameters[D, U, EO, EI, B <: Data]
(
  blocks: Seq[() => DspBlock[D, U, EO, EI, B]],
  //blocks: Seq[(Parameters => DspBlock, String, BlockConnectionParameters, Option[SAMConfig])],

  biggestWidth: Int = 128,
  writeHeader: Boolean = false
)

/*
class DspChain[D, U, EO, EI, B <: Data](val params: DspChainParameters[D, U, EO, EI, B])(implicit p: Parameters) extends HierarchicalDspBlock[Int, D, U, EO, EI, B] {
  val blocks = params.blocks
}
*/
