// See LICENSE for license details

package dspblocks

import chisel3._
import freechips.rocketchip.amba.ahb._
import freechips.rocketchip.amba.apb._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

/**
  * Base trait for basic unit of computation
  * @tparam D Down parameter type
  * @tparam U Up parameter type
  * @tparam EO Edge-out parameter type
  * @tparam EI Edge-in parameter type
  * @tparam B Bundle parameter type
  */
trait DspBlock[D, U, EO, EI, B <: Data] extends LazyModule {
  /**
    * Diplomatic node for AXI4-Stream interfaces
    */
  val streamNode: AXI4StreamNodeHandle
  /**
    * Diplmatic node for memory interface
    * Some blocks might not need memory mapping, so this is an Option[]
    */
  val mem: Option[MixedNode[D, U, EI, B, D, U, EO, B]]
}

/**
  * Mixin for DspBlock to make them standalone (i.e., you can build them as a top-level module).
  * This is especially important for testing.
  *
  * Adds BundleBridges to the input and output sides of the AXI4Stream node
  * @tparam D Down parameter type
  * @tparam U Up parameter type
  * @tparam EO Edge-out parameter type
  * @tparam EI Edge-in parameter type
  * @tparam B Bundle parameter type
  */
trait StandaloneBlock[D, U, EO, EI, B <: Data] extends DspBlock[D, U, EO, EI, B] {
  val ioInNode = BundleBridgeSource(() => new AXI4StreamBundle(AXI4StreamBundleParameters(n = 8)))
  val ioOutNode = BundleBridgeSink[AXI4StreamBundle]()

  ioOutNode :=
    AXI4StreamToBundleBridge(AXI4StreamSlaveParameters()) :=
    streamNode :=
    BundleBridgeToAXI4Stream(AXI4StreamMasterParameters()) :=
    ioInNode

  val in = InModuleBody { ioInNode.makeIO() }
  val out = InModuleBody { ioOutNode.makeIO() }
}

/**
  * AXI4-flavor of standalone block. Adds BundleBridge AXI4 interface.
  */
trait AXI4StandaloneBlock extends StandaloneBlock[
  AXI4MasterPortParameters,
  AXI4SlavePortParameters,
  AXI4EdgeParameters,
  AXI4EdgeParameters,
  AXI4Bundle] {
  def standaloneParams = AXI4BundleParameters(addrBits = 64, dataBits = 64, idBits = 1)
  val ioMem = mem.map { m => {
    val ioMemNode = BundleBridgeSource(() => AXI4Bundle(standaloneParams))

    m :=
      BundleBridgeToAXI4(AXI4MasterPortParameters(Seq(AXI4MasterParameters("bundleBridgeToAXI4")))) :=
      ioMemNode

    val ioMem = InModuleBody { ioMemNode.makeIO() }
    ioMem
  }}
}

/**
  * APB-flavor of standalone block. Adds BundleBridge APB interface.
  */
trait APBStandaloneBlock extends StandaloneBlock[
  APBMasterPortParameters,
  APBSlavePortParameters,
  APBEdgeParameters,
  APBEdgeParameters,
  APBBundle] {
  def standaloneParams = APBBundleParameters(addrBits = 64, dataBits = 64)
  val ioMem = mem.map { m => {
    val ioMemNode = BundleBridgeSource(() => APBBundle(standaloneParams))
    m :=
      BundleBridgeToAPB(APBMasterPortParameters(Seq(APBMasterParameters("bundleBridgeToAPB")))) :=
      ioMemNode

    val ioMem = InModuleBody { ioMemNode.makeIO() }
    ioMem
  }}
}

/**
  * TL-flavor of standalone block. Adds BundleBridge TL interface.
  */
trait TLStandaloneBlock extends StandaloneBlock[
  TLClientPortParameters,
  TLManagerPortParameters,
  TLEdgeOut,
  TLEdgeIn,
  TLBundle] {
  def standaloneParams = TLBundleParameters(addressBits = 64, dataBits = 64, sourceBits = 1,
    sinkBits = 1, sizeBits = 6,
    echoFields = Seq(), requestFields = Seq(), responseFields = Seq(),
    hasBCE = false)
  val ioMem = mem.map { m => {
    val ioMemNode = BundleBridgeSource(() => TLBundle(standaloneParams))
    m :=
      BundleBridgeToTL(TLClientPortParameters(Seq(TLClientParameters("bundleBridgeToTL")))) :=
      ioMemNode
    val ioMem = InModuleBody { ioMemNode.makeIO() }
    ioMem
  }}
}

trait TLDspBlock extends DspBlock[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle]

trait TLDspBlockWithBus extends TLDspBlock {
  val bus = LazyModule(new TLXbar)
  val mem = Some(bus.node)
}

trait APBDspBlock extends DspBlock[APBMasterPortParameters, APBSlavePortParameters, APBEdgeParameters, APBEdgeParameters, APBBundle]

trait APBDspBlockWithBus extends APBDspBlock {
  val bus = LazyModule(new APBFanout)
  val mem = Some(bus.node)
}

object AXI4DspBlock {
  type AXI4Node = MixedNode[
    AXI4MasterPortParameters, AXI4SlavePortParameters, AXI4EdgeParameters, AXI4Bundle,
    AXI4MasterPortParameters, AXI4SlavePortParameters, AXI4EdgeParameters, AXI4Bundle
    ]
}

trait AXI4DspBlock extends DspBlock[AXI4MasterPortParameters, AXI4SlavePortParameters, AXI4EdgeParameters, AXI4EdgeParameters, AXI4Bundle]

trait AXI4DspBlockWithBus extends AXI4DspBlock {
  val bus = LazyModule(new AXI4Xbar)
  val mem = Some(bus.node)
}

trait AHBSlaveDspBlock extends DspBlock[AHBMasterPortParameters, AHBSlavePortParameters, AHBEdgeParameters, AHBEdgeParameters, AHBSlaveBundle]

trait AHBSlaveDspBlockWithBus extends AHBSlaveDspBlock {
  val bus = LazyModule(new AHBFanout)
  val mem = Some(bus.node)
}
