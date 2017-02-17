// See LICENSE for license details.

// Author: Stevo Bailey (stevo.bailey@berkeley.edu)

package sam

import chisel3.util._
import chisel3._
import dspblocks._
import dspjunctions._
import scala.math._
import dsptools.numbers.{Real, DspComplex}
import dsptools.numbers.implicits._
import dsptools._
import cde.Parameters
import _root_.junctions._
import uncore.converters._
import uncore.tilelink._
import _root_.util._
import testchipip._

class SAMIO()(implicit p: Parameters) extends NastiBundle()(p) {
  val config = p(SAMKey(p(DspBlockId)))
  val w = (ceil(p(DspBlockKey(p(DspBlockId))).inputWidth*1.0/nastiXDataBits)*nastiXDataBits).toInt

  val in = Input(ValidWithSync(UInt(w.W)))
  val out = Flipped(new NastiIO())

  val wWriteCount = Output(UInt(config.memAddrBits.W))
  val wStartAddr = Input(UInt(config.memAddrBits.W))
  val wTargetCount = Input(UInt(config.memAddrBits.W))
  val wWaitForSync = Input(Bool())
  val wTrig = Input(Bool())
  val wPacketCount = Output(UInt(log2Up(config.bufferDepth).W))
  val wSyncAddr = Output(UInt(config.memAddrBits.W))
}

class SAM()(implicit p: Parameters) extends NastiModule()(p) {
  val io = IO(new SAMIO)
  val config = p(SAMKey(p(DspBlockId)))
  // [stevo]: make width a multiple of nastiDataWidth
  val w = (ceil(p(DspBlockKey(p(DspBlockId))).inputWidth*1.0/nastiXDataBits)*nastiXDataBits).toInt

  // memories
  // TODO: ensure that the master never tries to read beyond the depth of the SeqMem
  val mem = SeqMem(config.memDepth, UInt(w.W))

  // AXI4-Stream side
  val wIdle :: wReady :: wRecord :: Nil = Enum(Bits(), 3)
  val wState = Reg(init = wIdle)
  val wWriteCount = Reg(init = 0.U(config.memAddrBits.W))
  val wWriteAddr = Reg(init = 0.U(config.memAddrBits.W))
  val wPacketCount = Reg(init = 0.U(log2Up(config.bufferDepth).W))
  val wSyncAddr = Reg(init = 0.U(config.memAddrBits.W))
  val synced = Reg(init = false.B)
  val wTrigDelay = Reg(next=io.wTrig)
  io.wWriteCount := wWriteCount
  io.wPacketCount := wPacketCount
  when (wState === wIdle && io.wTrig && ~wTrigDelay) {
    wState := wRecord // go straight to record unless asked to wait
    wWriteAddr := io.wStartAddr
    wWriteCount := 0.U
    wPacketCount := 0.U
    wSyncAddr := io.wStartAddr
    synced := false.B
    when (io.wWaitForSync) {
      wState := wReady
    }
  }
  when (wState === wReady && io.in.sync && io.in.valid) {
    wState := wRecord
    wPacketCount := wPacketCount + 1.U
    synced := true.B // synced on address 0 when waiting for sync
  }
  when (wState === wRecord) {
    when (io.in.valid) {
      mem.write(wWriteAddr, io.in.bits)
      wWriteAddr := wWriteAddr + 1.U
      wWriteCount := wWriteCount + 1.U
      when (wWriteCount === io.wTargetCount - 1.U) {
        wState := wIdle
      } .elsewhen (io.in.sync) {
        wPacketCount := wPacketCount + 1.U // don't increment packet count if we stop next cycle
        when (~synced) {
          wSyncAddr := wWriteAddr + 1.U
          synced := true.B
        }
      }
      when (wWriteAddr === (config.memDepth-1).U) {
        wWriteAddr := 0.U  // loop back around
      }
    }
  }


  // TODO: ensure that the reading never happens beyond where the writing has occurred

  // AXI4 side
  val rIdle :: rWait :: rReadFirst :: rSend :: Nil = Enum(Bits(), 4)
  val rState = Reg(UInt(3.W), init=rIdle)
  val rAddr = Reg(UInt((nastiXAddrBits - log2Ceil(w/8)).W))
  val rLen = Reg(UInt((nastiXLenBits - log2Ceil(w/nastiXDataBits)).W))
  val rawData = mem.read(rAddr) // this will read every cycle; how do we make it single-ported?
  val rData = Reg(UInt(w.W))
  val rCount =
    if (w == nastiXDataBits) Reg(UInt(1.W))
    else Reg(UInt(log2Ceil(w/nastiXDataBits).W))
  val rId = Reg(io.out.ar.bits.id.cloneType)

  // state must be Idle here, since fire happens when ar.ready is high
  // grab the address information, then wait for data
  when (io.out.ar.fire()) {
    rAddr := io.out.ar.bits.addr >> (log2Ceil(w/8)).U
    rLen  := io.out.ar.bits.len  >> (log2Ceil(w/nastiXDataBits)).U
    rState := rWait
  }

  // delay state by a cycle to align with SeqMem, I think
  when (rState === rWait) { rState := rReadFirst }
  when (rState === rReadFirst) {
    rData := rawData
    rAddr := rAddr + 1.U
    rCount := (w/nastiXDataBits-1).U
    rId := io.out.ar.bits.id // seems unnecessary, since rId is always this
    rState := rSend
  }

  // wait for ready from master when in Send state
  when (io.out.r.fire()) {
    when (rCount === 0.U) {
      when (rLen === 0.U) {
        rState := rIdle
      } .otherwise {
        rData := rawData
        rAddr := rAddr + 1.U
        rLen := rLen - 1.U
        rCount := (w/nastiXDataBits-1).U
      }
    } .otherwise {
      rData := rData >> (nastiXDataBits).U
      rCount := rCount - 1.U
    }
  }

  io.out.ar.ready := (rState === rIdle)
  io.out.r.valid := (rState === rSend)
  io.out.r.bits := NastiReadDataChannel(
    id = rId,
    data = rData(nastiXDataBits - 1, 0),
    last = rLen === 0.U && rCount === 0.U)

  // no write capabilities yet
  io.out.aw.ready := false.B
  io.out.w.ready := false.B
  io.out.b.valid := false.B

  // for now
  require(w % nastiXDataBits == 0)

//  assert(!io.out.ar.valid ||
//    (io.out.ar.bits.addr(log2Ceil(w/8)-1, 0) === 0.U &&
//     io.out.ar.bits.len(log2Ceil(w/nastiXDataBits)-1, 0).andR &&
//     io.out.ar.bits.size === (log2Up(nastiXDataBits/8)).U),
//   "Invalid read request")

  // required by AXI4 spec
  when (reset) {
    io.out.r.valid := false.B
  }
}

class SAMWrapperIO()(implicit p: Parameters) extends BasicDspBlockIO()(p) {
  val config = p(SAMKey(p(DspBlockId)))

  val axi_out = Flipped(new NastiIO())
}

class LazySAM()(implicit p: Parameters) extends DspBlock()(p) {
  addControl("samWStartAddr", 0.U)
  addControl("samWTargetCount", 0.U)
  addControl("samWTrig", 0.U)
  addControl("samWWaitForSync", 0.U)

  addStatus("samWWriteCount")
  addStatus("samWPacketCount")
  addStatus("samWSyncAddr")

  lazy val module = new SAMWrapper(this)
}

class SAMWrapper(outer: LazySAM)(implicit p: Parameters) extends DspBlockModule(outer, Some(new SAMWrapperIO))(p) {

  // SCR
  val sam = Module(new SAM)
  val config = p(SAMKey(p(DspBlockId)))



  sam.io.in <> io.in
  sam.io.wStartAddr := control("samWStartAddr")(config.memAddrBits-1, 0)
  sam.io.wTargetCount := control("samWTargetCount")(config.memAddrBits-1, 0)
  sam.io.wTrig := control("samWTrig")(0)
  sam.io.wWaitForSync := control("samWWaitForSync")(0)

  status("samWWriteCount") := sam.io.wWriteCount
  status("samWPacketCount") := sam.io.wPacketCount
  status("samWSyncAddr") := sam.io.wSyncAddr

  io.asInstanceOf[SAMWrapperIO].axi_out <> sam.io.out
}
