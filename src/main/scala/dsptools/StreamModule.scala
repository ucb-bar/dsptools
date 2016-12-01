// See LICENSE for license details

package dsptools

import chisel3._
import chisel3.util.log2Up
import chisel3.internal.throwException
import dsptools.junctions._
import cde._
// Don't import dsptools.junctions._
import _root_.junctions._
import uncore.tilelink._
import rocketchip.PeripheryUtils
import testchipip._

case object StreamBlockKey extends Field[StreamBlockParameters]

trait StreamBlockParameters {
  def genIn [T <: Data]: T
  def genOut[T <: Data]: T = genIn[T]
  val lanes: Int
}

trait HasStreamBlockParameters[T <: Data, V <: Data] {
  implicit val p: Parameters
  val streamExternal = p(StreamBlockKey)
  def genIn(dummy: Int = 0):  T = streamExternal.genIn[T]
  def genOut(dummy: Int = 0): V = streamExternal.genOut[V]
  val lanes = streamExternal.lanes
}

class StreamBlockIO[T <: Data, V <: Data]()(implicit val p: Parameters) extends Bundle 
  with HasStreamBlockParameters[T, V] {
  def makePort[U <: Data](in: U) = {
    val vec = Vec(lanes, in.cloneType)
    val uint = Wire(vec).asUInt
    ValidWithSync(uint)
  }
  val in = Input(makePort(genIn))
  val out = Output(makePort(genOut))
  val axi = new NastiIO().flip

  override def cloneType: this.type = new StreamBlockIO()(p).asInstanceOf[this.type]
}

abstract class StreamBlock[T <: Data, V <: Data](override_clock: Option[Clock]=None, override_reset: Option[Bool]=None)
  (implicit val p: Parameters) extends Module(override_clock, override_reset) with HasStreamBlockParameters[T, V] {
    def baseAddr: BigInt
    def packed_input  = pack(Vec(lanes, genIn))
    def packed_output = pack(genOut)
    def pack[T <: Data](in: T): UInt = {
      val unpadded = Wire(in).asUInt
      val topad = (8 - (unpadded.getWidth % 8)) % 8
      unpadded.pad(topad)
    }
    val io = IO(new StreamBlockIO[T, V])
    val unpacked_input = {
      val i = Wire(ValidWithSync(Vec(lanes, genIn.cloneType)))
      i.valid := io.in.valid
      i.sync  := io.in.sync
      i.bits  fromBits io.in.bits
      i
    }
    val unpacked_output = {
      val o = Wire(ValidWithSync(Vec(lanes, genOut.cloneType)))
      io.out.valid := o.valid
      io.out.sync  := o.sync
      io.out.bits  := o.bits.asUInt
      o
    }

    var scrbuilt : Boolean = false
    val scrbuilder = new SCRBuilder(name)
    lazy val scr: SCRFile = {
      scrbuilt = true
      val tl = Wire(new ClientUncachedTileLinkIO)
      val scr_ = scrbuilder.generate(baseAddr)
      tl <> scr_.io.tl
      PeripheryUtils.convertTLtoAXI(tl) <> io.axi
      scr_
    }

    def addControl(name: String, init: UInt = null) = {
      require(!scrbuilt, 
        s"Called addControl after SCR has been built." + 
        s"Move your control() and status() calls after all addControl calls"
      )
      scrbuilder.addControl(name, init)
    }
    def addStatus(name: String) {
      require(!scrbuilt,
        s"Called addControl after SCR has been built." + 
        s"Move your control() and status() calls after all addControl calls"
      )
      scrbuilder.addStatus(name)
    }

    def control(name: String) = scr.control(name)
    def status(name : String) = scr.status(name)
}

abstract class StreamBlockTester[T <: Data, U <: Data, V <: StreamBlock[T, U]](dut: V) 
  extends DspTester(dut) {
  var streamInValid: Boolean = true
  def pauseStream: Unit = streamInValid = false
  def playStream:  Unit = streamInValid = true
  val streamIn: Seq[BigInt]
  private val streamInIter = streamIn.iterator
  private val streamOut_ = new scala.collection.mutable.Queue[BigInt]
  val streamOut: Seq[BigInt] = streamOut_
  def done = !streamInIter.hasNext

  val axi = dut.io.axi

  def aw_fire: Boolean = {
    (peek(axi.aw.valid) != BigInt(0)) &&
    (peek(axi.aw.ready) != BigInt(0))
  }

  def w_fire: Boolean = {
    (peek(axi.w.valid) != BigInt(0)) &&
    (peek(axi.w.ready) != BigInt(0))
  }

  val axiDataWidth = dut.io.axi.w.bits.data.getWidth
  val axiDataBytes = axiDataWidth / 8
  def axiWrite(addr: Int, value: Int): Unit = {
    // s_write_addr
    poke(axi.aw.valid,   1)
    poke(axi.aw.bits.id, 0)
    poke(axi.aw.bits.addr,    addr)
    poke(axi.aw.bits.size,    log2Up(axiDataBytes))
    poke(axi.aw.bits.len,     0)

    while (!aw_fire) {
      step(1)
    }
    poke(axi.aw.valid,   0)

    // s_write_data
    poke(axi.w.valid, 1)
    poke(axi.w.bits.data, value)
    poke(axi.w.bits.strb, -1)
    poke(axi.w.bits.last, 1)

    while (!w_fire) {
      step(1)
    }

    poke(axi.w.valid, 0)

    // s_write_stall

    // s_write_resp
  }

  def axiRead(addr: Int): Int = {
    0
  }

  override def step(n: Int): Unit = {
    for (i <- 0 until n) {
      if (streamInValid && streamInIter.hasNext) {
        poke(dut.io.in.bits, streamInIter.next)
      }
      if (peek(dut.io.out.valid) != BigInt(0)) {
        streamOut_ += peek(dut.io.out.bits)
      }
      super.step(1)
    }
  }
}

