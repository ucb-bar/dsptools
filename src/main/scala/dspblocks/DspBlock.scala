// See LICENSE for license details

package dspblocks

import cde._
import chisel3._
import dspjunctions._
// import junctions would import dsptools.junctions._
import _root_.junctions._
import uncore.tilelink._
import uncore.converters._
import rocketchip._
import rocketchip.PeripheryUtils
import diplomacy._
import testchipip._
//import dsptools.Utilities._
import scala.math._
import ipxact._

case object DspBlockId extends Field[String]
case class DspBlockKey(id: String) extends Field[DspBlockParameters]

case class DspBlockParameters (
  inputWidth: Int,
  outputWidth: Int
)

trait HasDspBlockParameters {
  implicit val p: Parameters
  // def baseAddr: Int = p(BaseAddr(p(DspBlockId)))
  def dspBlockExternal = p(DspBlockKey(p(DspBlockId)))
  def inputWidth  = dspBlockExternal.inputWidth
  def outputWidth = dspBlockExternal.outputWidth
  def id: String = p(DspBlockId)
}

// uses DspBlockId
case class GenKey(id: String) extends Field[GenParameters]

trait GenParameters {
  def genIn [T <: Data]: T
  def genOut[T <: Data]: T = genIn[T]
  def lanesIn: Int
  def lanesOut: Int = lanesIn
}

trait HasGenParameters[T <: Data, V <: Data] extends HasDspBlockParameters {
  def genExternal            = p(GenKey(p(DspBlockId)))
  def genIn(dummy: Int = 0)  = genExternal.genIn[T]
  def genOut(dummy: Int = 0) = genExternal.genOut[V]
  def lanesIn                = genExternal.lanesIn
  def lanesOut               = genExternal.lanesOut
  override def inputWidth    = lanesIn * genIn().getWidth
  override def outputWidth   = lanesOut * genOut().getWidth
  // todo some assertions that the width is correct
}

trait HasGenDspParameters[T <: Data, V <: Data] extends HasDspBlockParameters with HasGenParameters[T, V] {
  def portSize[U <: Data](lanes: Int, in: U): Int = {
    val unpadded = lanes * in.getWidth
    val topad = (8 - (unpadded % 8)) % 8
    unpadded + topad
  }
  abstract override def inputWidth     = portSize(lanesIn,  genIn())
  abstract override def outputWidth    = portSize(lanesOut, genOut())
}

trait DspBlockIO {
  def inputWidth: Int
  def outputWidth: Int
  implicit val p: Parameters

  val in  = Input( ValidWithSync(UInt(inputWidth.W)))
  val out = Output(ValidWithSync(UInt(outputWidth.W)))
  val axi = Flipped(new NastiIO())
}

class BasicDspBlockIO()(implicit val p: Parameters) extends Bundle with HasDspBlockParameters with DspBlockIO {
  override def cloneType: this.type = new BasicDspBlockIO()(p).asInstanceOf[this.type]
}

// case class BaseAddr(id: String) extends Field[Int]

trait HasBaseAddr {
  private var _baseAddr: BigInt = BigInt(0)
  def baseAddr: BigInt = _baseAddr
  def setBaseAddr(base: BigInt): Unit = {
    _baseAddr = base
  }
}
trait HasAddrMapEntry {
  val p: Parameters
  private def addrMapEntryName = p(DspBlockId)
  private def addrMapEntrySize = BigInt(1 << 8)
  def addrMapEntry = AddrMapEntry(addrMapEntryName,
    MemSize(addrMapEntrySize, MemAttr(AddrMapProt.RW))
    )
}

trait HasSCRBuilder {
  val p: Parameters
  private val scrName = p(DspBlockId)
  val scrbuilder = new SCRBuilder(scrName)
}

abstract class DspBlock()(implicit val p: Parameters) extends LazyModule
    with HasDspBlockParameters with HasBaseAddr with HasAddrMapEntry with HasSCRBuilder {
  override def module: DspBlockModule

  def size = scrbuilder.controlNames.length + scrbuilder.statusNames.length

  addStatus("uuid")
  addControl("wrapback")

  def addControl(name: String, init: UInt = null) = {
    scrbuilder.addControl(name, init)
  }
  def addStatus(name: String) {
    scrbuilder.addStatus(name)
  }

}

abstract class DspBlockModule(val outer: DspBlock, b: => Option[Bundle with DspBlockIO] = None)
  (implicit val p: Parameters) extends LazyModuleImp(outer) with HasDspBlockParameters {
  val io: Bundle with DspBlockIO = IO(b.getOrElse(new BasicDspBlockIO))

  def addrmap = testchipip.SCRAddressMap(outer.scrbuilder.devName).get

  val baseAddr = outer.baseAddr

  def unpackInput[T <: Data](lanes: Int, genIn: T) = {
    val i = Wire(ValidWithSync(Vec(lanes, genIn.cloneType)))
    i.valid := io.in.valid
    i.sync  := io.in.sync
    val w = i.bits.fromBits(io.in.bits)
    i.bits  := w
    i
  }
  def unpackOutput[T <: Data](lanes: Int, genOut: T) = {
    val o = Wire(ValidWithSync(Vec(lanes, genOut.cloneType)))
    io.out.valid := o.valid
    io.out.sync  := o.sync
    io.out.bits  := o.bits.asUInt
    o
  }

  lazy val scr: SCRFile = {
    println(s"Base address for $name is ${outer.baseAddr}")
    val scr_ = outer.scrbuilder.generate(outer.baseAddr)
    val tl2axi = Module(new TileLinkIONastiIOConverter())
    tl2axi.io.tl <> scr_.io.tl
    io.axi <> tl2axi.io.nasti
    scr_
  }

  def control(name: String) = scr.control(name)
  def status(name : String) = scr.status(name)

  status("uuid") := this.hashCode.U
}

class GenDspBlockIO[T <: Data, V <: Data]()(implicit val p: Parameters)
  extends Bundle with HasGenDspParameters[T, V] with DspBlockIO {
  override def cloneType = new GenDspBlockIO()(p).asInstanceOf[this.type]
}

abstract class GenDspBlockModule[T <: Data, V <: Data]
  (outer: DspBlock)(implicit p: Parameters) extends DspBlockModule(outer, Some(new GenDspBlockIO[T, V]))
  with HasGenDspParameters[T, V]

