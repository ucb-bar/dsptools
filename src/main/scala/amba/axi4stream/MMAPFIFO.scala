package freechips.rocketchip.amba.axi4stream

import chisel3._
import chisel3.internal.firrtl.Width
import chisel3.util._
import chisel3.iotesters._
import dspblocks._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._
import freechips.rocketchip.amba.apb._
import freechips.rocketchip.regmapper._

import scala.collection.mutable


class APBInStreamOutFIFO(val csrBase: Int, val memAddress: AddressSet, val beatBytes: Int = 4)(implicit p: Parameters) extends LazyModule with APBHasCSR with APBDspBlock
{
  require(mem.isDefined, "Requires a memory interface")

  // We require the address range to include an entire beat (for the write mask)
  require((memAddress.mask & (beatBytes - 1)) == beatBytes - 1)

  val csrSize = 512
  addControl("start")
  addControl("end")
  addControl("en") //, width=1.W)
  addControl("repeat") //, width=1.W)
  val csrs = makeCSRs()

  val internal = LazyModule(new APBInStreamOutFIFOInternal(memAddress, beatBytes, new CSRRecord(csrMap)))

  // Only used because this is being used as top level
  val outerAPB = APBBlindInputNode(Seq(APBMasterPortParameters(
    Seq(
      APBMasterParameters(
        "abc")
    ))))
  val streamNode = AXI4StreamBlindOutputNode(Seq(AXI4StreamSlavePortParameters(Seq(AXI4StreamSlaveParameters(
    bundleParams = AXI4StreamBundleParameters(1)
    )))))

  mem.get := outerAPB
  internal.memNode := mem.get
  streamNode := internal.streamNode

  lazy val module = new APBInStreamOutFIFOModule(this)

}

class APBInStreamOutFIFOModule(outer: APBInStreamOutFIFO) extends LazyModuleImp(outer) {

  val io = IO(new Bundle {
    val mem = outer.outerAPB.bundleIn
    val stream = outer.streamNode.bundleOut(0)
  })

  outer.internal.module.io.csrs := outer.csrs.module.io.csrs
}

class APBInStreamOutFIFOInternal(val memAddress: AddressSet, val beatBytes: Int = 4, val csrsIO: CSRRecord)(implicit p: Parameters) extends LazyModule
{
  val memNode = APBSlaveNode(Seq(APBSlavePortParameters(
    Seq(
      APBSlaveParameters(
        address    = Seq(memAddress),
        regionType = RegionType.UNCACHED,
        executable = false,
        supportsRead = true,
        supportsWrite = true)
    ),
  beatBytes = beatBytes)))

  val streamNode = AXI4StreamMasterNode(Seq(AXI4StreamMasterPortParameters(
    Seq(AXI4StreamMasterParameters(
      "fifoOut",
      AXI4StreamBundleParameters(
        n = beatBytes)
      )))))

  lazy val module = new APBInStreamOutFIFOInternalModule(this)

}

class APBInStreamOutFIFOInternalModule(outer: APBInStreamOutFIFOInternal) extends LazyModuleImp(outer) {

  val io = IO(new Bundle {
    val csrs = Input(outer.csrsIO.cloneType)
    val mem  = outer.memNode.bundleIn
    val stream = outer.streamNode.bundleOut
  })

  val csrStart                            = io.csrs("start")
  val csrEnd                              = io.csrs("end")
  val csrEn                               = io.csrs("en")
  val csrRepeat: Bool = io.csrs("repeat")(0)

  val memAddress: AddressSet = outer.memAddress
  val beatBytes : Int                                              = outer.beatBytes

  val count                          = RegInit(0.U(64.W))
  val enPrev                         = RegNext(csrEn)
  val done: Bool = RegNext(count) === csrEnd && !csrRepeat
  val advanceCount                   = Wire(Bool())
  advanceCount := false.B

  when (!csrEn) {
    count := csrStart
  } .elsewhen (advanceCount) {
    val nextCount = Wire(UInt())
    count := nextCount
    val countInc = count + 1.U
    nextCount := countInc
    when (countInc > csrEnd) {
      when (csrRepeat) {
        nextCount := csrStart
      } .otherwise {
        nextCount := count
      }
    }
  }

  def bigBits(x: BigInt, tail: List[Boolean] = List.empty[Boolean]): List[Boolean] =
    if (x == 0) tail.reverse else bigBits(x >> 1, ((x & 1) == 1) :: tail)

  val read : Bool = io.mem(0).psel && !io.mem(0).penable && !io.mem(0).pwrite
  val write: Bool = io.mem(0).psel && !io.mem(0).penable && io.mem(0).pwrite
  val mask : List[Boolean]      = bigBits(memAddress.mask >> log2Ceil(beatBytes))
  val inPaddr                         = Cat((mask zip (io.mem(0).paddr >> log2Ceil(beatBytes)).toBools).filter(_._1).map(_._2).reverse)

  val paddr                           = Mux(read || write, inPaddr, count)
  val legal: Bool = memAddress.contains(io.mem(0).paddr)

  val mem = SyncReadMem(1 << mask.count(b => b), Vec(beatBytes, Bits(width = 8.W)))

  when (write && legal) {
    mem.write(paddr, Vec.tabulate(beatBytes) { i => io.mem(0).pwdata(8 * (i + 1) - 1, 8 * i) }, io.mem(0).pstrb.toBools)
  }

  io.mem(0).pready  := true.B
  io.mem(0).pslverr := RegNext(!legal)
  io.mem(0).prdata  := mem.readAndHold(paddr, read).asUInt

  val streamData = Wire(UInt())
  val streamDataReg = Reg(UInt())
  val streamDataRegValid = RegInit(false.B)

  when (!read && !write) {
    streamData := mem.read(paddr).asUInt
  }
  when (ShiftRegister(!read && !write, 2)) {
    streamDataReg := streamData
    streamDataRegValid := true.B
  }

  io.stream(0).valid := RegNext(!read && !write)
  io.stream(0).bits.data := streamData

  when (streamDataRegValid) {
    io.stream(0).valid := true.B
    io.stream(0).bits.data := streamDataReg
  }

  when (io.stream(0).fire()) {
    streamDataRegValid := false.B
    advanceCount := true.B
  }

  // TODO: valid should not go high->low until it sees a ready
  when (!csrEn) {
    io.stream(0).valid := false.B
  }

  io.stream(0).bits.last := RegNext(count === csrEnd)
}

package tester {

  class MMAPFIFOTester(c: APBInStreamOutFIFOModule) extends
    PeekPokeTester(c) with APBMasterModel[APBInStreamOutFIFOModule] {
    def memAPB = c.io.mem(0)

    poke(c.io.stream.ready, 0)

    // write data
    apbWrite(0x200, 10)
    apbWrite(0x208, 11)
    apbWrite(0x210, 12)
    apbWrite(0x218, 13)
    apbWrite(0x220, 14)
    apbWrite(0x228, 15)
    apbWrite(0x230, 16)
    apbWrite(0x238, 17)

    // start stream out
    apbWrite(16, 7)
    apbWrite(24, 3)
    apbWrite(8, 0x0fffffff)
    apbWrite(0, 0x0fffffff)

    step(10)
    poke(c.io.stream.ready, 1)

    for (i <- 0 until 30) {
      step(1)
      val svalid = peek(c.io.stream.valid)
      val sdata = peek(c.io.stream.bits.data)
      println(s"$i:\tVALID=$svalid\tDATA=$sdata")
    }

    poke(c.io.stream.ready, 0)
    apbWrite(8, 0)
    poke(c.io.stream.ready, 1)
    step(20)
    poke(c.io.stream.ready, 0)
    apbWrite(8, 0xfffffff)
    poke(c.io.stream.ready, 1)

    for (i <- 30 until 60) {
      step(1)
      val svalid = peek(c.io.stream.valid)
      val sdata = peek(c.io.stream.bits.data)
      println(s"$i:\tVALID=$svalid\tDATA=$sdata")
    }
  }
}

object JustForNow {
  def main(args: Array[String]): Unit = {
    import freechips.rocketchip.coreplex._
    implicit val p: Parameters = Parameters.root((new BaseCoreplexConfig).toInstance)
    val dut = () => LazyModule(new APBInStreamOutFIFO(0, AddressSet(0x200, 0xff), 8)(p)).module
    chisel3.Driver.execute(Array("-X", "verilog"), dut)

    chisel3.iotesters.Driver.execute(Array("--backend-name", "firrtl", "-fiwv"), dut) { c=> new tester.MMAPFIFOTester(c) }
  }
}
