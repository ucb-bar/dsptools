package freechips.rocketchip.amba.axi4stream

import chisel3._
import chisel3.util.{Decoupled, Queue, log2Ceil}
import dspblocks.AXI4DspBlock
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._

/**
  * Bundle class for dma request descriptors
  * @param addrWidth width of address
  * @param lenWidth width of length
  */
class DMARequest
(
  val addrWidth: Int,
  val lenWidth: Int,
) extends Bundle {
  /**
    * Base address of DMA request
    */
  val baseAddress: UInt = UInt(addrWidth.W)
  /**
    * Length of DMA request, minus 1. length = 0 corresponds to a single beat
    */
  val length: UInt = UInt(lenWidth.W)
  /**
    * Default value is zero. If > 0, then do the same read or write @cycles number of times
    */
  val cycles: UInt = UInt(addrWidth.W)
  /**
    * If false, addresses considered are from @baseAddress to @baseAddress + @length
    * If true, addresses considered are @baseAddress only, in fixed mode
    */
  val fixedAddress: UInt = Bool()
}
/**
  * Companion object factory
  */
object DMARequest {
  def apply(addrWidth: Int, lenWidth: Int): DMARequest = {
    new DMARequest(addrWidth = addrWidth, lenWidth = lenWidth)
  }
}

class SimpleDMARequest(addrWidth: Int, lenWidth: Int) extends Bundle {
  /**
    * Base address of DMA request
    */
  val baseAddress: UInt = UInt(addrWidth.W)
  /**
    * Length of DMA request, minus 1. length = 0 corresponds to a single beat
    */
  val length: UInt = UInt(lenWidth.W)
  /**
    * If false, addresses considered are from @baseAddress to @baseAddress + @length
    * If true, addresses considered are @baseAddress only, in fixed mode
    */
  val fixedAddress: UInt = Bool()

  override def cloneType: this.type =
    SimpleDMARequest(addrWidth = addrWidth, lenWidth = lenWidth).asInstanceOf[this.type]
}
object SimpleDMARequest {
  def apply(addrWidth: Int, lenWidth: Int): SimpleDMARequest =
    new SimpleDMARequest(addrWidth = addrWidth, lenWidth = lenWidth)
}

class DMASimplifierIO(addrWidth: Int, complexLenWidth: Int, simpleLenWidth: Int) extends Bundle {
  val in = Flipped(Decoupled(DMARequest(addrWidth, complexLenWidth)))
  val out = Decoupled(SimpleDMARequest(addrWidth, simpleLenWidth))

  override def cloneType: this.type =
    new DMASimplifierIO(addrWidth = addrWidth, complexLenWidth = complexLenWidth, simpleLenWidth = simpleLenWidth).
      asInstanceOf[this.type]
}
class DMASimplifier(val addrWidth: Int, val complexLenWidth: Int, val simpleLenWidth: Int, val beatBytes: Int)
  extends Module {
  require(complexLenWidth >= simpleLenWidth)

  val io = IO(new DMASimplifierIO(addrWidth, complexLenWidth, simpleLenWidth))

  val maxSimpleLength = (BigInt(1) << simpleLenWidth) - 1
  val lengthCnt = RegInit(0.U((complexLenWidth - simpleLenWidth).W))
  val cycleCnt = RegInit(0.U(addrWidth.W))

  val lastBeat = lengthCnt >= (io.in.bits.length >> simpleLenWidth).asUInt
  val lastCycle = cycleCnt >= io.in.bits.cycles
  val last = lastBeat && lastCycle

  val base = io.in.bits.baseAddress + lengthCnt * (beatBytes * (maxSimpleLength + 1)).U
  val length = Mux(lastBeat, io.in.bits.length - lengthCnt * maxSimpleLength.U, maxSimpleLength.U)

  io.out.bits.baseAddress := base
  io.out.bits.length := length
  io.out.bits.fixedAddress := io.in.bits.fixedAddress

  io.in.ready := last && io.out.ready
  when (io.out.fire()) {
    lengthCnt := Mux(lastBeat, 0.U, lengthCnt + 1.U)
    cycleCnt := Mux(lastBeat, cycleCnt + 1.U, cycleCnt)
  }
  when (io.in.fire()) {
    lengthCnt := 0.U
    cycleCnt := 0.U
  }
  io.out.valid := io.in.valid
}

/**
  * AXI-4 Master <=> AXI-4 Stream DMA* @param id range of IDs allowed
  * @param id id range, but only one is used
  * @param aligned aligned accesses only
  */
class StreamingAXI4DMA
(
  id: IdRange = IdRange(0, 1),
  aligned: Boolean = false,
) extends LazyModule()(Parameters.empty) {

  val streamNode = AXI4StreamIdentityNode()
  val axiNode = AXI4MasterNode(Seq(AXI4MasterPortParameters(
    Seq(AXI4MasterParameters(
      name = name,
      id = id,
      aligned = aligned,
      maxFlight = Some(2)
    )))))

  lazy val module = new LazyModuleImp(this) {
    val (in, inP) = streamNode.in.head
    val (out, outP) = streamNode.out.head
    val (axi, axiP) = axiNode.out.head

    require(inP.bundle.hasData)
    require(!inP.bundle.hasKeep)
    require(inP.bundle.n * 8 == axiP.bundle.dataBits,
      s"Streaming interface was ${inP.bundle.n * 8} bits, axi was ${axiP.bundle.dataBits}")

    val addrWidth: Int = axiP.bundle.addrBits
    val lenWidth: Int = axiP.bundle.lenBits
    val beatLength: Int = 1 << lenWidth
    val dataWidth: Int = axiP.bundle.dataBits
    val beatBytes = dataWidth >> 3

    val enable = IO(Input(Bool()))
    // val softReset = IO(Input(Bool()))
    val idle = IO(Output(Bool()))

    val watchdogInterval = IO(Input(UInt(64.W)))

    val readComplete = IO(Output(Bool()))
    val readWatchdog = IO(Output(Bool()))
    val readError = IO(Output(Bool()))
    val writeComplete = IO(Output(Bool()))
    val writeWatchdog = IO(Output(Bool()))
    val writeError = IO(Output(Bool()))

    val streamToMemRequest = IO(Flipped(Decoupled(
      DMARequest(addrWidth = addrWidth, lenWidth = addrWidth)
    )))
    val streamToMemLengthRemaining = IO(Output(UInt(lenWidth.W)))

    val streamToMemQueue = Module(new Queue(DMARequest(addrWidth = addrWidth, lenWidth = addrWidth), 8))
    streamToMemQueue.io.enq <> streamToMemRequest

    val streamToMemQueueCount = IO(Output(UInt()))
    streamToMemQueueCount := streamToMemQueue.io.count

    val streamToMemSimple = Module(
      new DMASimplifier(addrWidth = addrWidth, complexLenWidth = addrWidth, simpleLenWidth = lenWidth, beatBytes = beatBytes))
    streamToMemSimple.io.in <> streamToMemQueue.io.deq

    val memToStreamRequest = IO(Flipped(Decoupled(
      DMARequest(addrWidth = addrWidth, lenWidth = addrWidth)
    )))
    val memToStreamLengthRemaining = IO(Output(UInt(lenWidth.W)))

    val memToStreamQueue = Module(new Queue(DMARequest(addrWidth = addrWidth, lenWidth = addrWidth), 8))
    memToStreamQueue.io.enq <> memToStreamRequest

    val memToStreamQueueCount = IO(Output(UInt()))
    memToStreamQueueCount := memToStreamQueue.io.count

    val memToStreamSimple = Module(
      new DMASimplifier(addrWidth = addrWidth, complexLenWidth = addrWidth, simpleLenWidth = lenWidth, beatBytes = beatBytes))
    memToStreamSimple.io.in <> memToStreamQueue.io.deq

    val reading = RegInit(false.B)
    val writing = RegInit(false.B)

    val readDescriptor = Reg(SimpleDMARequest(addrWidth = addrWidth, lenWidth = lenWidth))
    val writeDescriptor = Reg(SimpleDMARequest(addrWidth = addrWidth, lenWidth = lenWidth))

    val readBeatCounter = Reg(UInt(lenWidth.W))
    val writeBeatCounter = Reg(UInt(lenWidth.W))

    memToStreamLengthRemaining := Mux(reading, readDescriptor.length - readBeatCounter, 0.U)
    streamToMemLengthRemaining := Mux(writing, writeDescriptor.length - writeBeatCounter, 0.U)

    val readWatchdogCounter = RegInit(0.U(64.W))
    val writeWatchdogCounter = RegInit(0.U(64.W))

    val arprot = IO(Input(UInt(AXI4Parameters.protBits.W)))
    val awprot = IO(Input(UInt(AXI4Parameters.protBits.W)))
    val arcache = IO(Input(UInt(AXI4Parameters.cacheBits.W)))
    val awcache = IO(Input(UInt(AXI4Parameters.cacheBits.W)))

    // Set some defaults
    readComplete := false.B
    readWatchdog := readWatchdogCounter > watchdogInterval
    readError := false.B
    writeComplete := false.B
    writeWatchdog := writeWatchdogCounter > watchdogInterval
    writeError := false.B

    axi.ar.valid := !reading && enable && memToStreamSimple.io.out.valid
    memToStreamSimple.io.out.ready := !reading && enable && axi.ar.ready

    when (memToStreamSimple.io.out.fire()) {
      reading := true.B
      readDescriptor := memToStreamSimple.io.out.bits
      readBeatCounter := 0.U
      readWatchdogCounter := 0.U
    }

    axi.aw.valid := !writing && enable && streamToMemSimple.io.out.valid
    streamToMemSimple.io.out.ready := !writing && enable && axi.aw.ready
    when (streamToMemSimple.io.out.fire()) {
      writing := true.B
      writeDescriptor := streamToMemSimple.io.out.bits
      writeBeatCounter := 0.U
      writeWatchdogCounter := 0.U
    }

    when (reading) {
      readWatchdogCounter := readWatchdogCounter + 1.U
    }
    when (writing) {
      writeWatchdogCounter := writeWatchdogCounter + 1.U
    }

    val readBuffer = Module(new Queue(chiselTypeOf(axi.r.bits), beatLength))
    readBuffer.io.enq <> axi.r
    readBuffer.io.deq.ready := out.ready
    out.valid := readBuffer.io.deq.valid
    out.bits.data := readBuffer.io.deq.bits.data
    out.bits.strb := ((BigInt(1) << out.params.n) - 1).U

    axi.ar.bits.addr := memToStreamSimple.io.out.bits.baseAddress
    axi.ar.bits.burst := !memToStreamSimple.io.out.bits.fixedAddress
    axi.ar.bits.cache := arcache
    axi.ar.bits.id := id.start.U
    axi.ar.bits.len := memToStreamSimple.io.out.bits.length
    axi.ar.bits.lock := 0.U // normal access
    axi.ar.bits.prot := arprot
    axi.ar.bits.size := log2Ceil((dataWidth + 7) / 8).U

    when (axi.r.fire()) {
      readBeatCounter := readBeatCounter + 1.U
      // todo: readDescriptor isn't correct for a single-beat single-cycle transaction (are they possible?)
      when (axi.r.bits.last || readBeatCounter >= readDescriptor.length) {
        readComplete := true.B
        reading := false.B
      }
      readError := axi.r.bits.resp =/= AXI4Parameters.RESP_OKAY
    }
    // Stream to AXI write
    val writeBuffer = Module(new Queue(in.bits.cloneType, beatLength))
    writeBuffer.io.enq <> in
    axi.w.bits.data := writeBuffer.io.deq.bits.data
    axi.w.bits.strb := writeBuffer.io.deq.bits.makeStrb
    axi.w.bits.last := Mux(axi.aw.fire(), // check if single beat
      axi.aw.bits.len === 0.U, // if single beat, writeBeatCounter and writeDescriptor won't be set
      writeBeatCounter >= writeDescriptor.length
    )
    axi.w.valid := writing && writeBuffer.io.deq.valid
    writeBuffer.io.deq.ready := axi.w.ready && writing

    axi.aw.bits.addr := streamToMemSimple.io.out.bits.baseAddress
    axi.aw.bits.burst := !streamToMemSimple.io.out.bits.fixedAddress
    axi.aw.bits.cache := awcache
    axi.aw.bits.id := id.start.U
    axi.aw.bits.len := streamToMemSimple.io.out.bits.length
    axi.aw.bits.lock := 0.U // normal access
    axi.aw.bits.prot := awprot
    axi.aw.bits.size := log2Ceil((dataWidth + 7) / 8).U

    when (axi.w.fire()) {
      writeBeatCounter := writeBeatCounter + 1.U
      when (axi.w.bits.last) {
        writing := false.B
        writeComplete := true.B
      }
    }

    axi.b.ready := true.B // should probably monitor responses!

    when (axi.b.fire()) {
      writeError := axi.b.bits.resp =/= AXI4Parameters.RESP_OKAY
    }

    idle :=
        !reading &&
        !writing &&
        writeBuffer.io.count === 0.U &&
        readBuffer.io.count === 0.U
  }
}

class StreamingAXI4DMAWithCSR
(
  csrAddress: AddressSet,
  beatBytes: Int = 4,
  id: IdRange = IdRange(0, 1),
  aligned: Boolean = false,
) extends LazyModule()(Parameters.empty) { outer =>

  val dma = LazyModule(new StreamingAXI4DMA(id = id, aligned = aligned))

  val axiMasterNode = dma.axiNode
  val axiSlaveNode = AXI4RegisterNode(address = csrAddress, beatBytes = beatBytes)

  val streamNode = dma.streamNode

  lazy val module = new LazyModuleImp(this) {
    val dma = outer.dma.module

    val enReg = RegInit(false.B)
    val watchdogReg = RegInit(0.U(32.W))
    val intReg = RegInit(0.U(6.W))

    dma.enable := enReg

    when (dma.readComplete) {
      intReg := intReg | 1.U
    }
    when (dma.readWatchdog) {
      intReg := intReg | 2.U
    }
    when (dma.readError) {
      intReg := intReg | 4.U
    }
    when (dma.writeComplete) {
      intReg := intReg | 8.U
    }
    when (dma.writeWatchdog) {
      intReg := intReg | 16.U
    }
    when (dma.writeError) {
      intReg := intReg | 32.U
    }

    val s2mbits = dma.streamToMemRequest.bits
    val m2sbits = dma.memToStreamRequest.bits
    val s2mBaseAddress = RegInit(0.U(s2mbits.addrWidth.W))
    val s2mLength = RegInit(0.U(s2mbits.addrWidth.W))
    val s2mCycles = RegInit(0.U(s2mbits.addrWidth.W))
    val s2mFixedAddress = RegInit(false.B)
    val m2sBaseAddress = RegInit(0.U(s2mbits.addrWidth.W))
    val m2sLength = RegInit(0.U(s2mbits.addrWidth.W))
    val m2sCycles = RegInit(0.U(s2mbits.addrWidth.W))
    val m2sFixedAddress = RegInit(false.B)

    s2mbits.baseAddress := s2mBaseAddress
    s2mbits.length := s2mLength
    s2mbits.cycles := s2mCycles
    s2mbits.fixedAddress := s2mFixedAddress

    m2sbits.baseAddress := m2sBaseAddress
    m2sbits.length := m2sLength
    m2sbits.cycles := m2sCycles
    m2sbits.fixedAddress := m2sFixedAddress

    // Defaults are for Xilinx -> HPx interfaces
    val arprot = RegInit(0.U(AXI4Parameters.protBits.W))
    val awprot = RegInit(0.U(AXI4Parameters.protBits.W))
    val arcache = RegInit(AXI4Parameters.CACHE_MODIFIABLE | AXI4Parameters.CACHE_BUFFERABLE)
    val awcache = RegInit(AXI4Parameters.CACHE_MODIFIABLE | AXI4Parameters.CACHE_BUFFERABLE)

    dma.arprot := arprot
    dma.awprot := awprot
    dma.arcache := arcache
    dma.awcache := awcache

    axiSlaveNode.regmap(
      axiSlaveNode.beatBytes * 0 -> Seq(RegField(1, enReg)),
      axiSlaveNode.beatBytes * 1 -> Seq(RegField.r(1, dma.idle)),
      axiSlaveNode.beatBytes * 2 -> Seq(RegField(beatBytes * 8, watchdogReg)),
      axiSlaveNode.beatBytes * 3 -> Seq(RegField(6, intReg)),
      axiSlaveNode.beatBytes * 4 -> Seq(RegField(beatBytes * 8, s2mBaseAddress)),
      axiSlaveNode.beatBytes * 5 -> Seq(RegField(beatBytes * 8, s2mLength)),
      axiSlaveNode.beatBytes * 6 -> Seq(RegField(beatBytes * 8, s2mCycles)),
      axiSlaveNode.beatBytes * 7 -> Seq(RegField(1, s2mFixedAddress)),
      axiSlaveNode.beatBytes * 8 -> Seq(RegField(beatBytes * 8,
        dma.streamToMemLengthRemaining,
        RegWriteFn((valid, data) => {
          dma.streamToMemRequest.valid := valid
          dma.streamToMemRequest.ready
        }))),
      axiSlaveNode.beatBytes * 9  -> Seq(RegField(beatBytes * 8, m2sBaseAddress)),
      axiSlaveNode.beatBytes * 10 -> Seq(RegField(beatBytes * 8, m2sLength)),
      axiSlaveNode.beatBytes * 11 -> Seq(RegField(beatBytes * 8, m2sCycles)),
      axiSlaveNode.beatBytes * 12 -> Seq(RegField(1, m2sFixedAddress)),
      axiSlaveNode.beatBytes * 13 -> Seq(RegField(beatBytes * 8,
        dma.memToStreamLengthRemaining,
        RegWriteFn((valid, data) => {
          dma.memToStreamRequest.valid := valid
          dma.memToStreamRequest.ready
        }))),
      axiSlaveNode.beatBytes * 14 -> Seq(RegField(AXI4Parameters.protBits, arprot)),
      axiSlaveNode.beatBytes * 15 -> Seq(RegField(AXI4Parameters.protBits, awprot)),
      axiSlaveNode.beatBytes * 16 -> Seq(RegField(AXI4Parameters.cacheBits, arcache)),
      axiSlaveNode.beatBytes * 17 -> Seq(RegField(AXI4Parameters.cacheBits, awcache)),
    )
  }
}

class StreamingAXI4DMAWithMemory
(
  address: AddressSet,
  id: IdRange = IdRange(0, 1),
  aligned: Boolean = false,
  executable: Boolean = true,
  beatBytes: Int = 4,
  devName: Option[String] = None,
  errors: Seq[AddressSet] = Nil,
  wcorrupt: Boolean = false
) extends LazyModule()(Parameters.empty) {

  val dma = LazyModule(new StreamingAXI4DMA(id = id, aligned = aligned))
  val lhs = AXI4StreamIdentityNode()
  val rhs = AXI4StreamIdentityNode()
  rhs := dma.streamNode := lhs
  val streamNode = NodeHandle(lhs.inward, rhs.outward)

  val ram = AXI4RAM(
    address=address,
    executable=executable,
    beatBytes=beatBytes,
    devName = devName,
    errors = errors,
    cacheable = false,
  )

  ram := AXI4Fragmenter() := dma.axiNode

  lazy val module = new LazyModuleImp(this) {
    val (streamIn, inP) = lhs.in.head
    val (streamOut, outP) = rhs.out.head
    // val axiP =
    val addrWidth = dma.module.addrWidth
    val lenWidth = dma.module.lenWidth
    val beatLength = 1 << lenWidth
    val dataWidth = dma.module.dataWidth


    val io = IO(new Bundle {
      val enable = Input(Bool())
      // val softReset = IO(Input(Bool()))
      val idle = Output(Bool())

      val watchdogInterval = Input(UInt(64.W))

      val readComplete = Output(Bool())
      val readWatchdog = Output(Bool())
      val readError = Output(Bool())
      val writeComplete = Output(Bool())
      val writeWatchdog = Output(Bool())
      val writeError = Output(Bool())

      val streamToMemRequest = Flipped(Decoupled(
        DMARequest(addrWidth = addrWidth, lenWidth = lenWidth)
      ))
      val streamToMemLengthRemaining = Output(UInt(lenWidth.W))

      val memToStreamRequest = Flipped(Decoupled(
        DMARequest(addrWidth = addrWidth, lenWidth = lenWidth)
      ))
      val memToStreamLengthRemaining = Output(UInt(lenWidth.W))
    })

    dma.module.enable := io.enable
    io.idle := dma.module.enable
    dma.module.watchdogInterval := io.watchdogInterval
    io.readComplete := dma.module.readComplete
    io.readWatchdog := dma.module.readWatchdog
    io.readError := dma.module.readError
    io.writeComplete := dma.module.writeComplete
    io.writeWatchdog := dma.module.writeWatchdog
    io.writeError := dma.module.writeError

    dma.module.streamToMemRequest <> io.streamToMemRequest
    dma.module.memToStreamRequest <> io.memToStreamRequest

    io.streamToMemLengthRemaining := dma.module.streamToMemLengthRemaining
    io.memToStreamLengthRemaining := dma.module.memToStreamLengthRemaining
  }
}

class StreamingAXI4DMAWithCSRWithScratchpad
(
  val csrAddress: AddressSet,
  val scratchpadAddress: AddressSet,
  val beatBytes: Int = 4,
  val id: IdRange = IdRange(0, 1),
  val aligned: Boolean = false,
) extends LazyModule()(Parameters.empty) with AXI4DspBlock {
  val dma = LazyModule(new StreamingAXI4DMAWithCSR(csrAddress, beatBytes, id, aligned))

  val streamNode = NodeHandle(dma.streamNode.inward, dma.streamNode.outward)
  val mem = Some(AXI4IdentityNode())

  val ram = AXI4RAM(
    address = scratchpadAddress,
    executable = false,
    beatBytes = beatBytes,
    devName = Some("bwrc,dmascratchpad"),
    errors = Nil,
  )

  val ramXbar = AXI4Xbar()
  val topXbar = AXI4Xbar()

  // ram := AXI4Fragmenter() := ramXbar
  ram := ramXbar
  ramXbar := AXI4Fragmenter() := dma.axiMasterNode
  ramXbar := topXbar
  dma.axiSlaveNode := topXbar
  topXbar := mem.get

  lazy val module = new LazyModuleImp(this) {
    mem.get.out.foreach { o => o._2.slave.slaves.foreach(s => println(s"${s.name} is ${s.interleavedId}")) }
  }
}
