// See LICENSE for license details


package dsptools

import chisel3._
import chisel3.internal.throwException
import dsptools.junctions._
import cde._
// Don't import dsptools.junctions._
import _root_.junctions._
import uncore.tilelink._
import rocketchip.PeripheryUtils
import testchipip._

class StreamBlockIO(genIn: UInt, genOut: UInt)(implicit p: Parameters) extends Bundle {
  val in = Input(ValidWithSync(genIn))
  val out = Output(ValidWithSync(genOut))
  val axi = new NastiIO().flip

  override def cloneType: this.type = new StreamBlockIO(genIn.cloneType, genOut.cloneType)(p).asInstanceOf[this.type]
}

abstract class StreamBlock[T <: Data, V <: Data](input: => T, output: => T, override_clock: Option[Clock]=None, override_reset: Option[Bool]=None)
  (implicit p: Parameters) extends Module(override_clock, override_reset) {
    def baseAddr: BigInt
    def packed_input  = pack(input)
    def packed_output = pack(output)
    def pack[T <: Data](in: T): UInt = {
      val unpadded = Wire(in).asUInt
      val topad = (8 - (unpadded.getWidth % 8)) % 8
      unpadded.pad(topad)
    }
    val io = IO(new StreamBlockIO(packed_input.cloneType, packed_output.cloneType))
    val unpacked_input = {
      val i = Wire(ValidWithSync(input.cloneType))
      i.valid := io.in.valid
      i.sync  := io.in.sync
      i.bits  fromBits io.in.bits
      i
    }
    val unpacked_output = {
      val o = Wire(ValidWithSync(output.cloneType))
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

abstract class StreamBlockTester[T <: Data, U <: Data, V <: StreamBlock[T, U]](val dut: V) 
  extends DspTester(dut) {
  val streamIn: Seq[BigInt]
  private val streamOut_ = new scala.collection.mutable.Queue
  val streamOut: Seq[BigInt] = streamOut_
  def axiWrite(addr: Int, value: Int): Unit = {
  }

  def axiRead(addr: Int): Int = {
    0
  }

  override def step(n: Int): Unit = {
    for (i <- 0 until n) {
      if (peek(dut.io.out.valid) != 0) {
        streamOut_ += peek(dut.io.out.bits)
      }
      super.step(1)
    }
  }
}

