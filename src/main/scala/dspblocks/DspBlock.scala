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

case object DspBlockKey extends Field[DspBlockParameters]

case class DspBlockParameters (
  inputWidth: Int,
  outputWidth: Int
)

trait HasDspBlockParameters {
  implicit val p: Parameters
  def inputWidth  = p(DspBlockKey).inputWidth
  def outputWidth = p(DspBlockKey).outputWidth
}

case object GenKey extends Field[GenParameters]

trait GenParameters {
  def genIn [T <: Data]: T
  def genOut[T <: Data]: T = genIn[T]
  def lanesIn: Int
  def lanesOut: Int = lanesIn
}

trait HasGenParameters[T <: Data, V <: Data] {
  implicit val p: Parameters
  def genExternal = p(GenKey)
  def genIn(dummy: Int = 0)  = genExternal.genIn[T]
  def genOut(dummy: Int = 0) = genExternal.genOut[V]
  def lanesIn  = genExternal.lanesIn
  def lanesOut = genExternal.lanesOut
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
  val axi = new NastiIO().flip
}

class BasicDspBlockIO()(implicit val p: Parameters) extends Bundle with HasDspBlockParameters with DspBlockIO {
  override def cloneType: this.type = new BasicDspBlockIO()(p).asInstanceOf[this.type]
}

case object BaseAddr extends Field[Int]

trait HasLazyDspBlockParameters {
  implicit val p: Parameters
  def baseAddr: Int = p(BaseAddr)
}

abstract class LazyDspBlock()(implicit val p: Parameters) extends LazyModule with HasLazyDspBlockParameters {
  override def module: DspBlock
  val scrbuilder = new SCRBuilder(name)

  def size = scrbuilder.controlNames.length + scrbuilder.statusNames.length

  addStatus("uuid")

  def addControl(name: String, init: UInt = null) = {
    scrbuilder.addControl(name, init)
  }
  def addStatus(name: String) {
    scrbuilder.addStatus(name)
  }

  println(s"Base address for $name is $baseAddr")
}

abstract class DspBlock(outer: LazyDspBlock, b: => Option[Bundle with DspBlockIO] = None)
  (implicit val p: Parameters) extends LazyModuleImp(outer) with HasDspBlockParameters {
  val io: Bundle with DspBlockIO = IO(b.getOrElse(new BasicDspBlockIO))

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
    val scr_ = outer.scrbuilder.generate(outer.baseAddr)
    val tl2axi = Module(new TileLinkIONastiIOConverter())
    tl2axi.io.tl <> scr_.io.tl
    io.axi <> tl2axi.io.nasti
    scr_
  }

  def control(name: String) = scr.control(name)
  def status(name : String) = scr.status(name)
}

class GenDspBlockIO[T <: Data, V <: Data]()(implicit val p: Parameters)
  extends Bundle with HasGenDspParameters[T, V] with DspBlockIO {
  override def cloneType = new GenDspBlockIO()(p).asInstanceOf[this.type]
}

abstract class GenDspBlock[T <: Data, V <: Data]
  (outer: LazyDspBlock)(implicit p: Parameters) extends DspBlock(outer, Some(new GenDspBlockIO[T, V]))
  with HasGenDspParameters[T, V]

