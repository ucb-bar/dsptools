// See LICENSE for license details

package dsptools

import chisel3._
import chisel3.util.log2Up
import chisel3.internal.throwException
import dsptools.junctions._
import cde._
// import junctions would import dsptools.junctions._
import _root_.junctions._
import uncore.tilelink._
import uncore.converters._
import rocketchip.PeripheryUtils
import testchipip._

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

abstract class DspBlock(b: => Option[Bundle with DspBlockIO] = None, override_clock: Option[Clock]=None, override_reset: Option[Bool]=None)
  (implicit val p: Parameters) extends Module(override_clock, override_reset) with HasDspBlockParameters {
  def baseAddr: BigInt
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

  var scrbuilt : Boolean = false
  val scrbuilder = new SCRBuilder(name)
  lazy val scr: SCRFile = {
    scrbuilt = true
    //val tl = Wire(new ClientUncachedTileLinkIO)
    val scr_ = scrbuilder.generate(baseAddr)
    //tl <> scr_.io.tl
    //PeripheryUtils.convertTLtoAXI(tl) <> io.axi
    val tl2axi = Module(new TileLinkIONastiIOConverter())
    tl2axi.io.tl <> scr_.io.tl
    io.axi <> tl2axi.io.nasti
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

class GenDspBlockIO[T <: Data, V <: Data]()(implicit val p: Parameters)
  extends Bundle with HasGenDspParameters[T, V] with DspBlockIO {
  override def cloneType = new GenDspBlockIO()(p).asInstanceOf[this.type]
}

abstract class GenDspBlock[T <: Data, V <: Data]
  (override_clock: Option[Clock]=None, override_reset: Option[Bool]=None)
  (implicit p: Parameters) extends DspBlock(Some(new GenDspBlockIO[T, V]), override_clock, override_reset)
  with HasGenDspParameters[T, V]

abstract class DspBlockTester[V <: DspBlock](dut: V, maxWait: Int = 100)
  extends DspTester(dut) {
  var streamInValid: Boolean = true
  def pauseStream(): Unit = streamInValid = false
  def playStream():  Unit = streamInValid = true
  def streamIn: Seq[BigInt]
  private lazy val streamInIter = streamIn.iterator
  private val streamOut_ = new scala.collection.mutable.Queue[BigInt]
  val streamOut: Seq[BigInt] = streamOut_
  def done = !streamInIter.hasNext

  val axi = dut.io.axi

  def aw_ready: Boolean = {
    // (peek(axi.aw.valid) != BigInt(0)) &&
    (peek(axi.aw.ready) != BigInt(0))
  }

  def w_ready: Boolean = {
    // (peek(axi.w.valid) != BigInt(0)) &&
    (peek(axi.w.ready) != BigInt(0))
  }

  def b_ready: Boolean = {
    (peek(axi.b.valid) != BigInt(0)) // &&
    // (peek(axi.b.ready) != BigInt(0))
  }

  poke(axi.aw.valid, 0)
  poke(axi.ar.valid, 0)
  poke(axi.b.ready,  0)

  val axiDataWidth = dut.io.axi.w.bits.data.getWidth
  val axiDataBytes = axiDataWidth / 8
  val burstLen = axiDataBytes
  def axiWrite(addr: Int, value: BigInt): Unit = {
    var waited = 0
    while (!aw_ready) {
      require(waited < maxWait, "AXI AW not ready")
      //if (waited >= maxWait) return
      step(1)
      waited += 1
    } 

    // s_write_addr
    poke(axi.aw.valid,   1)
    poke(axi.aw.bits.id, 0)
    poke(axi.aw.bits.user, 0)
    poke(axi.aw.bits.addr,    addr)
    poke(axi.aw.bits.len,     0)
    poke(axi.aw.bits.size,    log2Up(axiDataBytes))
    poke(axi.aw.bits.lock, 0)
    poke(axi.aw.bits.cache, 0)
    poke(axi.aw.bits.prot, 0)
    poke(axi.aw.bits.qos, 0)
    poke(axi.aw.bits.region, 0)

    step(1)

    poke(axi.aw.valid,   0)

    waited = 0
    do {
      // if (waited >= maxWait) return
      require(waited < maxWait, "AXI W not ready")
      step(1)
      waited += 1
    } while (!w_ready)

    // s_write_data
    poke(axi.w.valid, 1)
    poke(axi.w.bits.data, value)
    poke(axi.w.bits.strb, 0xFF)
    poke(axi.w.bits.last, 1)
    poke(axi.w.bits.id, 0)
    poke(axi.w.bits.user, 0)

    step(1)

    poke(axi.w.valid, 0)
    poke(axi.w.bits.last, 0)

    // s_write_stall

    waited = 0
    do {
      require(waited < maxWait, "AXI B not ready")
      //if (waited >= maxWait) return
      step(1)
      waited += 1
    } while (!b_ready)
    // s_write_resp

    poke(axi.b.ready, 1)


    step(1)
    poke(axi.b.ready, 0)
  }
  def axiWrite(addr: Int, value: Int): Unit = axiWrite(addr, BigInt(value))

  def axiRead(addr: Int): BigInt = {
    0
  }

  override def step(n: Int): Unit = {
    //for (i <- 0 until n) {
      if (streamInValid && streamInIter.hasNext) {
        poke(dut.io.in.valid, 1)
        poke(dut.io.in.bits, streamInIter.next)
      } else {
        poke(dut.io.in.valid, 0)
      }
      if (peek(dut.io.out.valid) != BigInt(0)) {
        streamOut_ += peek(dut.io.out.bits)
      }
      super.step(1)
      if (n > 1) step(n - 1)
    //}
  }
}

