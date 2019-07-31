package dspblocks

import chisel3._
import chisel3.util.{Queue, log2Ceil}
import freechips.rocketchip.amba.ahb._
import freechips.rocketchip.amba.apb._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream.AXI4StreamIdentityNode
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy.LazyModuleImp
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._

trait DspQueue[D, U, EO, EI, B <: Data] extends DspBlock[D, U, EO, EI, B] {
  /**
    * Depth of queue
    */
  val depth: Int

  require(depth > 0)

  override val streamNode: AXI4StreamIdentityNode = AXI4StreamIdentityNode()
}

trait DspQueueImp [D, U, EO, EI, B <: Data] extends LazyModuleImp with HasRegMap {
  def outer: DspQueue[D, U, EO, EI, B] = wrapper.asInstanceOf[DspQueue[D, U, EO, EI, B]]
  val streamNode = outer.streamNode
  val depth      = outer.depth

  val (streamIn, streamEdgeIn)   = streamNode.in.head
  val (streamOut, streamEdgeOut) = streamNode.out.head


  val queuedStream = Queue(streamIn, entries = depth)
  streamOut <> queuedStream

  val queueEntries = RegInit(UInt(log2Ceil(depth + 1).W), 0.U)
  queueEntries := queueEntries + streamIn.fire() - streamOut.fire()

  val queueThreshold = WireInit(UInt(64.W), depth.U)
  val queueFilling = queueEntries >= queueThreshold
  val queueFull    = queueEntries >= depth.U

  override val interrupts: Vec[Bool] = VecInit(queueFilling, queueFull)
  regmap(0 ->
    Seq(RegField(64, queueThreshold,
      RegFieldDesc("queueThreshold", "Threshold for number of elements to throw interrupt"))))

}

class TLDspQueue(val depth: Int, val baseAddr: BigInt = 0, devname: String = "vqueue", concurrency: Int = 1)(implicit p: Parameters)
  extends TLRegisterRouter(baseAddr, devname, Seq("ucb-bar,vreg"), beatBytes = 8, concurrency = concurrency)(
    new TLRegBundle(depth, _))(
    new TLRegModule(depth, _, _)
      with DspQueueImp[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle]
  ) with DspQueue[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle] with TLDspBlock {
  override val mem = Some(node)
}

class AXI4DspQueue(val depth: Int, val baseAddr: BigInt = 0, concurrency: Int = 4)(implicit p: Parameters)
  extends AXI4RegisterRouter(baseAddr, beatBytes = 8, concurrency = concurrency)(
    new AXI4RegBundle(depth, _))(
    new AXI4RegModule(depth, _, _)
      with DspQueueImp[AXI4MasterPortParameters, AXI4SlavePortParameters, AXI4EdgeParameters, AXI4EdgeParameters, AXI4Bundle]
  ) with DspQueue[AXI4MasterPortParameters, AXI4SlavePortParameters, AXI4EdgeParameters, AXI4EdgeParameters, AXI4Bundle]
    with AXI4DspBlock {
  val mem = Some(node)
}

class AHBDspQueue(val depth: Int, val baseAddr: BigInt = 0, concurrency: Int = 4)(implicit p: Parameters)
  extends AHBRegisterRouter(baseAddr, beatBytes = 8, concurrency = concurrency)(
    new AHBRegBundle(depth, _))(
    new AHBRegModule(depth, _, _)
      with DspQueueImp[AHBMasterPortParameters, AHBSlavePortParameters, AHBEdgeParameters, AHBEdgeParameters, AHBSlaveBundle]
  ) with DspQueue[AHBMasterPortParameters, AHBSlavePortParameters, AHBEdgeParameters, AHBEdgeParameters, AHBSlaveBundle]
    with AHBSlaveDspBlock {
  val mem = Some(node)
}

class APBDspQueue(val depth: Int, val baseAddr: BigInt = 0, concurrency: Int = 4)(implicit p: Parameters)
  extends APBRegisterRouter(baseAddr, beatBytes = 8, concurrency = concurrency)(
    new APBRegBundle(depth, _))(
    new APBRegModule(depth, _, _)
      with DspQueueImp[APBMasterPortParameters, APBSlavePortParameters, APBEdgeParameters, APBEdgeParameters, APBBundle]
  ) with DspQueue[APBMasterPortParameters, APBSlavePortParameters, APBEdgeParameters, APBEdgeParameters, APBBundle]
    with APBDspBlock {
  val mem = Some(node)
}

