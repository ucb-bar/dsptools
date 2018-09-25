package dspblocks

import chisel3._
import chisel3.util.log2Ceil
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream.{AXI4StreamIdentityNode, AXI4StreamSlavePortParameters}
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._

trait DspRegister[D, U, EO, EI, B <: Data] extends DspBlock[D, U, EO, EI, B] {
  /**
    * Maximum length of the vector register
    */
  val len: Int
  /**
    * Condition to include the contents of the register in the memory map
    */
  val mapMem: Boolean

  require(len > 0)

  override val streamNode: AXI4StreamIdentityNode = AXI4StreamIdentityNode()
}

trait DspRegisterImp[D, U, EO, EI, B <: Data] extends LazyModuleImp with HasRegMap {
  def outer: DspRegister[D, U, EO, EI, B] = wrapper.asInstanceOf[DspRegister[D, U, EO, EI, B]]
  val maxlen     = outer.len
  val mapMem     = outer.mapMem
  val streamNode = outer.streamNode

  val (streamIn, streamEdgeIn)   = streamNode.in.head
  val (streamOut, streamEdgeOut) = streamNode.out.head

  require(streamEdgeIn.bundle.hasData, "DspRegister registers data, must have data field!")
  require(!streamEdgeIn.bundle.hasKeep, "DspRegister does not support TKEEP")
  require(!streamEdgeIn.bundle.hasStrb, "DspRegister does not support TSTRB")
  require(streamEdgeIn.bundle.i == 0 && streamEdgeIn.bundle.d == 0, "DspRegister does not support TID or TDEST")

  val protoData      = UInt(streamIn.params.dataBits.W)
  val mem            = SyncReadMem(maxlen, protoData)
  val user           = Reg(protoData)

  val veclen         = RegInit(maxlen.U)

  // Load is reading from register to streamOut
  val loading  = RegInit(false.B)
  // Store is writing to register from streamIn
  val storing  = RegInit(false.B)

  val loadAfterStore = RegInit(false.B)
  val storeAfterLoad = !loadAfterStore

  val loadIdx  = Reg(UInt(log2Ceil(maxlen).W))
  val storeIdx = Reg(UInt(log2Ceil(maxlen).W))

  when (!loading) { loadIdx  := 0.U }
  when (!storing) { storeIdx := 0.U }

  val (memMap, memReadEn, memWriteEn) = MemToRegmap(streamIn.bits.data.cloneType, mem, true.B, true.B, 24, wordSizeBytes = Some(8))

  val loadOK  = (!storing || storeAfterLoad || (loadIdx < storeIdx)) && !memReadEn
  val storeOK = (!loading || loadAfterStore || (storeIdx < loadIdx)) && !memWriteEn

  val readIdx = WireInit(loadIdx)
  val readEn  = !memReadEn
  val readMem = mem.read(readIdx) //, readEn)


  val writeEn  = WireInit(false.B)
  val writeIdx = WireInit(UInt(), 0.U)
  val writeVal = WireInit(UInt(), 0.U)
  when (writeEn) {
    mem.write(writeIdx, writeVal)
  }

  streamOut.bits.data := readMem
  when (!readEn) {
    streamOut.bits.data := 0.U
  }
  streamOut.bits.user := user
  streamOut.bits.last := loadIdx === (veclen - 1.U)
  streamOut.valid     := loading && loadOK

  when (loading && streamOut.fire()) {
    val next = loadIdx +& 1.U
    readIdx := next
    when (next === veclen) {
      loadIdx := 0.U
      readIdx := 0.U
      loading := false.B
    } .otherwise {
      loadIdx := next
      loading := true.B
    }
  }

  streamIn.ready := storing && storeOK //loading && loadOK

  when (storing && streamIn.fire()) {
    when (writeIdx === 0.U) {
      user := streamIn.bits.user
    }
    writeEn := true.B
    writeIdx := storeIdx
    writeVal := streamIn.bits.data

    val next = storeIdx +& 1.U
    when (next === veclen) {
      storeIdx := 0.U
      storing  := false.B
    } .otherwise {
      storeIdx := next
      storing  := true.B
    }
  }

  when (!loading && storing) {
    loadAfterStore := true.B
  }
  when (!storing) {
    loadAfterStore := false.B
  }

  regmap(
    Seq(0 -> Seq(
      RegField(
        64,
        RegReadFn(veclen),
        RegWriteFn((valid, data) => {
          when (valid) {
            veclen := Mux(data > maxlen.U, maxlen.U, data)
          }
          true.B
        }),
        RegFieldDesc("veclen", "Vector length register (if writing > len, sets to len)")
      )
    ),
    8 -> Seq(
      RegField(8,    loading,  RegFieldDesc("streamLoading", "Load from AXI-4 Stream")),
      RegField(8 + 48, storing,  RegFieldDesc("streamStoring", "Store to AXI-4 Stream"))
    ),
    16 -> Seq(
      RegField(64, user, RegFieldDesc("user", "TUSER"))
    )) ++ (if (mapMem) memMap else Seq()):_*
  )

}

class TLDspRegister(val len: Int, val mapMem: Boolean = true, val baseAddr: BigInt = 0, devname: String = "vreg", concurrency: Int = 1)(implicit p: Parameters)
  extends TLRegisterRouter(baseAddr, devname, Seq("ucb-bar,vreg"), beatBytes = 8, concurrency = concurrency)(
    new TLRegBundle(len, _))(
    new TLRegModule(len, _, _)
      with DspRegisterImp[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle]
  ) with DspRegister[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle] with TLDspBlock {
  override val mem = Some(node)
}

class AXI4DspRegister(val len: Int, val mapMem: Boolean = true, val baseAddr: BigInt = 0, concurrency: Int = 4)(implicit p: Parameters)
  extends AXI4RegisterRouter(baseAddr, beatBytes = 8, concurrency = concurrency)(
    new AXI4RegBundle(len, _))(
    new AXI4RegModule(len, _, _)
      with DspRegisterImp[AXI4MasterPortParameters, AXI4SlavePortParameters, AXI4EdgeParameters, AXI4EdgeParameters, AXI4Bundle]
  ) with DspRegister[AXI4MasterPortParameters, AXI4SlavePortParameters, AXI4EdgeParameters, AXI4EdgeParameters, AXI4Bundle]
    with AXI4DspBlock {
  val mem = Some(node)
}