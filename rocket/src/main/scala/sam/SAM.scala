// See LICENSE for license details.

// Author: Stevo Bailey (stevo.bailey@berkeley.edu)

package sam

// import chisel3.util._
// import chisel3._
// import dspblocks._
// import dspjunctions._
// import scala.math._
// import dsptools.numbers.{Real, DspComplex}
// import dsptools.numbers.implicits._
// import dsptools._
// import cde.Parameters
// import _root_.junctions._
// import uncore.converters._
// import uncore.tilelink._
// import _root_.util._
// import testchipip._
// 
// trait SAMWidthHelper extends HasNastiParameters {
//   val p: Parameters
//   val ioWidth = p(DspBlockKey(p(DspBlockId))).inputWidth
//   val wordsWidth = (ioWidth + nastiXDataBits - 1) / nastiXDataBits
//   val wordsWidthPowerOfTwo = 1 << util.log2Ceil(wordsWidth)
// 
//   val memWidth = wordsWidth * nastiXDataBits
//   val powerOfTwoWidth = wordsWidthPowerOfTwo * nastiXDataBits
// }
// 
// class SAMIO()(implicit p: Parameters) extends NastiBundle()(p) with SAMWidthHelper {
//   val config = p(SAMKey(p(DspBlockId)))
//   val w      = ioWidth
// 
//   val in  = Input(ValidWithSync(UInt(w.W)))
//   val out = Flipped(new NastiIO())
// 
//   val wWriteCount  = Output(UInt(config.memAddrBits.W))
//   val wStartAddr   = Input(UInt(config.memAddrBits.W))
//   val wTargetCount = Input(UInt(config.memAddrBits.W))
//   val wWaitForSync = Input(Bool())
//   val wTrig        = Input(Bool())
//   val wPacketCount = Output(UInt(log2Ceil(config.bufferDepth).W))
//   val wSyncAddr    = Output(UInt(config.memAddrBits.W))
//   val wState       = Output(UInt(2.W))
// }
// 
// class SAM()(implicit p: Parameters) extends NastiModule()(p) with SAMWidthHelper {
//   val io     = IO(new SAMIO)
//   val config = p(SAMKey(p(DspBlockId)))
//   // make width of the memory a multiple of nastiDataWidth
//   val w      = memWidth
// 
//   // memories
//   // TODO: ensure that the master never tries to read beyond the depth of the SeqMem
//   val mem = SeqMem(config.memDepth, UInt(w.W))
//   val mem_wen = Wire(Bool())
//   val mem_ren = Wire(Bool())
// 
//   // AXI4-Stream side
//   val wIdle :: wReady :: wRecord :: Nil = Enum(Bits(), 3)
//   val wState       = Reg(init = wIdle)
//   val wWriteCount  = Reg(init = 0.U(config.memAddrBits.W))
//   val wWriteAddr   = Reg(init = 0.U(config.memAddrBits.W))
//   val wPacketCount = Reg(init = 0.U(log2Up(config.bufferDepth).W))
//   val wSyncAddr    = Reg(init = 0.U(config.memAddrBits.W))
//   val synced       = Reg(init = false.B)
//   val wTrigDelay   = Reg(next = io.wTrig)
//   io.wWriteCount  := wWriteCount
//   io.wPacketCount := wPacketCount
//   io.wSyncAddr    := wSyncAddr
//   io.wState       := wState
// 
//   // mem_wen, mem_ren false unless set true later
//   mem_wen := false.B
//   mem_ren := false.B
//   when (wState === wIdle && io.wTrig && ~wTrigDelay) {
//     wState       := wRecord // go straight to record unless asked to wait
//     wWriteAddr   := io.wStartAddr
//     wWriteCount  := 0.U
//     wPacketCount := 0.U
//     wSyncAddr    := io.wStartAddr
//     synced       := false.B
//     when (io.wWaitForSync) {
//       wState := wReady
//     }
//   }
//   when (wState === wReady && io.in.sync && io.in.valid) {
//     wState       := wRecord
//     wPacketCount := wPacketCount + 1.U
//     synced       := true.B // synced on address 0 when waiting for sync
//   }
// 
//   // wen set only in wRecord
//   when (mem_wen) {
//       mem.write(wWriteAddr, io.in.bits)
//   }
//   when (wState === wRecord) {
//     when (io.in.valid) {
//       mem_wen     := true.B
//       wWriteAddr  := wWriteAddr + 1.U
//       wWriteCount := wWriteCount + 1.U
//       when (wWriteCount === io.wTargetCount - 1.U) {
//         wState := wIdle
//       } .elsewhen (io.in.sync) {
//         wPacketCount := wPacketCount + 1.U // don't increment packet count if we stop next cycle
//         when (~synced) {
//           wSyncAddr := wWriteAddr + 1.U
//           synced    := true.B
//         }
//       }
//       when (wWriteAddr === (config.memDepth-1).U) {
//         wWriteAddr := 0.U  // loop back around
//       }
//     }
//   }
// 
// 
//   // TODO: ensure that the reading never happens beyond where the writing has occurred
// 
//   // AXI4 side
//   val rIdle :: rWait :: rReadFirst :: rSend :: Nil = Enum(Bits(), 4)
//   val rWidth     = powerOfTwoWidth
//   val rState     = Reg(UInt(3.W), init = rIdle)
//   val rAddr      = Reg(UInt(log2Ceil(config.memDepth).W))
//   val rLen       = Reg(UInt((nastiXLenBits+1).W))
//   val rawData    = Wire(UInt(rWidth.W))
// 
//   val rData      = Reg(UInt(rWidth.W))
//   val rCount     = Reg(UInt(log2Ceil(rWidth/nastiXDataBits).W))
//   val rCountMask = ((BigInt(1) << rCount.getWidth) - 1).U
//   val rId        = Reg(io.out.ar.bits.id.cloneType)
// 
//   rawData       := mem.read(rAddr, mem_ren && !mem_wen) 
// 
//   // state must be Idle here, since fire happens when ar.ready is high
//   // grab the address information, then wait for data
//   when (io.out.ar.fire()) {
//     rAddr  := io.out.ar.bits.addr >> (log2Ceil(rWidth/8)).U
//     rCount := (io.out.ar.bits.addr >> 3.U) & rCountMask
//     rLen   := io.out.ar.bits.len + 1.U
//     rId    := io.out.ar.bits.id
//     rState := rWait
//     // mem_ren := true.B
//   }
// 
//   // delay state by a cycle to align with SeqMem, I think
//   when (rState === rWait) {
//     rState := rReadFirst
//     mem_ren := true.B
//   }
//   when (rState === rReadFirst) {
//     mem_ren := true.B
//     printf("ReadFirst with rCount 0x%x and rLen 0x%x and rData 0x%x and rAddr 0x%x\n", rCount, rLen, rData, rAddr)
//     rData  := rawData << (nastiXDataBits.U * rCount)
//     rAddr  := rAddr + 1.U
//     rCount := rCount + 1.U
//     rState := rSend
//     rLen   := rLen - 1.U
//   }
// 
//   // wait for ready from master when in Send state
//   when (io.out.r.fire()) {
//     printf("R fired with rCount 0x%x and rLen 0x%x and rData 0x%x and rAddr 0x%x\n", rCount, rLen, rData, rAddr)
//     mem_ren := true.B
//     when (rLen === 0.U) {
//       rState := rIdle
//     } .otherwise {
//       rLen := rLen - 1.U
//       rCount := rCount + 1.U
//       // This when() is written in this order because 0-width
//       // comparisons to non-zero width nodes are always false.
//       // This means when rWidth = nastiXDataBits that we
//       // are always reading from rawData and always incrementing
//       // rAddr.
//       when (rCount != 0.U) {
//         rData := rData << (nastiXDataBits).U
//       } .otherwise {
//         rData := rawData
//         rAddr := rAddr + 1.U
//       }
//     }
//   }
// 
//   io.out.ar.ready := (rState === rIdle)
//   io.out.r.valid := (rState === rSend)
//   io.out.r.bits := NastiReadDataChannel(
//     id = rId,
//     data = rData(rWidth -1, rWidth - nastiXDataBits),
//     last = rLen === 0.U)
// 
//   // no write capabilities yet
//   io.out.aw.ready := false.B
//   io.out.w.ready := false.B
//   io.out.b.valid := false.B
// 
//   require(rWidth % nastiXDataBits == 0)
// 
//   assert(!io.out.aw.valid, "Cannot write to SAM, don't use AW")
//   assert(!io.out.w.valid,  "Cannot write to SAM, don't use W")
// 
//   assert(!io.out.ar.fire() ||
//     io.out.ar.bits.burst === 1.U, "Only increment burst mode is supported")
//   assert(!io.out.ar.fire() ||
//     io.out.ar.bits.size === 3.U, "Only word-sized bursts are supported")
// 
// //  assert(!io.out.ar.valid ||
// //    (io.out.ar.bits.addr(log2Ceil(w/8)-1, 0) === 0.U &&
// //     io.out.ar.bits.len(log2Ceil(w/nastiXDataBits)-1, 0).andR &&
// //     io.out.ar.bits.size === (log2Up(nastiXDataBits/8)).U),
// //   "Invalid read request")
// 
//   // required by AXI4 spec
//   when (reset) {
//     io.out.r.valid := false.B
//   }
// }
// 
// class SAMWrapperIO()(implicit p: Parameters) extends BasicDspBlockIO()(p) {
//   val config = p(SAMKey(p(DspBlockId)))
// 
//   val axi_out = Flipped(new NastiIO())
// }
// 
// class SAMWrapper()(implicit p: Parameters) extends DspBlock()(p) with HasAddrMapEntry with HasBaseAddr with HasDataBaseAddr with HasNastiParameters {
//   val w = (ceil(p(DspBlockKey(p(DspBlockId))).inputWidth*1.0/nastiXDataBits)*nastiXDataBits).toInt
// 
//   addControl("samWStartAddr", 0.U)
//   addControl("samWTargetCount", 0.U)
//   addControl("samWTrig", 0.U)
//   addControl("samWWaitForSync", 0.U)
// 
//   addStatus("samWWriteCount")
//   addStatus("samWPacketCount")
//   addStatus("samWSyncAddr")
//   addStatus("samWState")
// 
//   val config = p(SAMKey(p(DspBlockId)))
//   lazy val module = new SAMWrapperModule(this)
// }
// 
// trait HasDataBaseAddr {
//   private var _dataBaseAddr: () => BigInt = () => BigInt(0)
//   def dataBaseAddr: BigInt = _dataBaseAddr()
//   def setDataBaseAddr(base: () => BigInt): Unit = {
//     _dataBaseAddr = base
//   }
// }
// 
// class SAMWrapperModule(outer: SAMWrapper)(implicit p: Parameters) extends DspBlockModule(outer, Some(new SAMWrapperIO))(p) {
// 
//   val dataBaseAddr = outer.dataBaseAddr
// 
//   println(s"Base address for $name (data) is ${outer.dataBaseAddr}")
// 
//   // SCR
//   val sam = Module(new SAM)
// 
//   // todo check that addr map entry is big enough
// 
//   val config = outer.config
// 
//   sam.io.in <> io.in
//   sam.io.wStartAddr   := control("samWStartAddr")(config.memAddrBits-1, 0)
//   sam.io.wTargetCount := control("samWTargetCount")(config.memAddrBits-1, 0)
//   sam.io.wTrig        := control("samWTrig")(0)
//   sam.io.wWaitForSync := control("samWWaitForSync")(0)
// 
//   status("samWWriteCount")  := sam.io.wWriteCount
//   status("samWPacketCount") := sam.io.wPacketCount
//   status("samWSyncAddr")    := sam.io.wSyncAddr
//   status("samWState")       := sam.io.wState
// 
//   io.asInstanceOf[SAMWrapperIO].axi_out <> sam.io.out
// }
