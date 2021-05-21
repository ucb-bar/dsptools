package freechips.rocketchip.amba.axi4stream

/*
class APBInStreamOutFIFO(val csrBase: Int, val memAddress: AddressSet, val beatBytes: Int = 4)(implicit p: Parameters) extends LazyModule with APBHasCSR with APBDspBlockWithBus
{
  require(mem.isDefined, "Requires a memory interface")

  // We require the address range to include an entire beat (for the write mask)
  require((memAddress.mask & (beatBytes - 1)) == beatBytes - 1)

  val csrSize = 512
  addControl("start")
  addControl("end")
  addControl("en") //, width=1.W)
  addControl("repeat") //, width=1.W)

  val internal = LazyModule(new APBInStreamOutFIFOInternal(memAddress, beatBytes, new CSRRecord(csrMap)))

  // Only used because this is being used as top level
  val outerAPB = APBIdentityNode()
  val streamNode = AXI4StreamIdentityNode()

  mem.get := outerAPB
  internal.memNode := mem.get
  streamNode := internal.streamNode

  lazy val module = new APBInStreamOutFIFOModule(this)

}

class APBInStreamOutFIFOModule(outer: APBInStreamOutFIFO) extends LazyRawModuleImp(outer) {
  // val (auto, dangles) = instantiate()

  val (mem, _) = outer.outerAPB.in.head
  val (stream, _) = outer.streamNode.out.head

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
      n = beatBytes
      )))))

  lazy val module = new APBInStreamOutFIFOInternalModule(this)

}

class APBInStreamOutFIFOInternalModule(outer: APBInStreamOutFIFOInternal) extends LazyModuleImp(outer) {

  val io = IO(new Bundle {
    val csrs = Input(outer.csrsIO.cloneType)
  })

  val (mem, _) = outer.memNode.in.unzip
  val (stream, _) = outer.streamNode.out.unzip

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

  val read : Bool = mem.head.psel && !mem.head.penable && !mem.head.pwrite
  val write: Bool = mem.head.psel && !mem.head.penable && mem.head.pwrite
  val mask : List[Boolean]      = bigBits(memAddress.mask >> log2Ceil(beatBytes))
  val inPaddr                         = Cat((mask zip (mem.head.paddr >> log2Ceil(beatBytes)).toBools).filter(_._1).map(_._2).reverse)

  val paddr                           = Mux(read || write, inPaddr, count)
  val legal: Bool = memAddress.contains(mem.head.paddr)

  val sram = SyncReadMem(1 << mask.count(b => b), Vec(beatBytes, Bits(width = 8.W)))

  when (write && legal) {
    sram.write(paddr, VecInit.tabulate(beatBytes) { i => mem.head.pwdata(8 * (i + 1) - 1, 8 * i) }, mem.head.pstrb.toBools)
  }

  mem.head.pready  := true.B
  mem.head.pslverr := RegNext(!legal)
  mem.head.prdata  := sram.readAndHold(paddr, read).asUInt

  val streamData = Wire(UInt())
  val streamDataReg = Reg(UInt())
  val streamDataRegValid = RegInit(false.B)

  when (!read && !write) {
    streamData := sram.read(paddr).asUInt
  }
  when (ShiftRegister(!read && !write, 2)) {
    streamDataReg := streamData
    streamDataRegValid := true.B
  }

  stream.head.valid := RegNext(!read && !write)
  stream.head.bits.data := streamData

  when (streamDataRegValid) {
    stream.head.valid := true.B
    stream.head.bits.data := streamDataReg
  }

  when (stream.head.fire()) {
    streamDataRegValid := false.B
    advanceCount := true.B
  }

  // TODO: valid should not go high->low until it sees a ready
  when (!csrEn) {
    stream.head.valid := false.B
  }

  stream.head.bits.last := RegNext(count === csrEnd)
}
*/
/*
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
*/