package freechips.rocketchip.amba.apb

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._

object APBBundleBridgeImp extends BundleBridgeImp[APBBundle]

case class APBToBundleBridgeNode(slaveParams: APBSlavePortParameters)(implicit valName: ValName)
  extends MixedAdapterNode(APBImp, APBBundleBridgeImp)(
    dFn = { masterParams =>
      BundleBridgeParams(() => APBBundle(APBBundleParameters(masterParams, slaveParams)))
    },
    uFn = { mp => slaveParams }
  )

object APBToBundleBridgeNode {
  def apply(slaveParams: APBSlaveParameters, beatBytes: Int)(implicit  valName: ValName): APBToBundleBridgeNode =
    new APBToBundleBridgeNode(APBSlavePortParameters(Seq(slaveParams), beatBytes))
}

class APBToBundleBridge(slaveParams: APBSlavePortParameters)(implicit p: Parameters) extends LazyModule {
  val node = APBToBundleBridgeNode(slaveParams)
  lazy val module = new LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, _), (out, _)) =>
      out <> in
    }
  }
}

object APBToBundleBridge {
  def apply(slaveParams: APBSlavePortParameters)(implicit p: Parameters): APBToBundleBridgeNode = {
    val converter = LazyModule(new APBToBundleBridge(slaveParams))
    converter.node
  }
  def apply(slaveParams: APBSlaveParameters, beatBytes: Int)(implicit p: Parameters): APBToBundleBridgeNode = {
    apply(APBSlavePortParameters(Seq(slaveParams), beatBytes))
  }
}

case class BundleBridgeToAPBNode(masterParams: APBMasterPortParameters)(implicit valName: ValName)
  extends MixedAdapterNode(APBBundleBridgeImp, APBImp)(
    dFn = { mp =>
      masterParams
    },
    uFn = { slaveParams => BundleBridgeParams(None) }
  )

object BundleBridgeToAPBNode {
  def apply(masterParams: APBMasterParameters)(implicit valName: ValName): BundleBridgeToAPBNode = {
    BundleBridgeToAPBNode(APBMasterPortParameters(Seq(masterParams)))
  }
}

class BundleBridgeToAPB(masterParams: APBMasterPortParameters)(implicit p: Parameters) extends LazyModule {
  val node = BundleBridgeToAPBNode(masterParams)
  lazy val module = new LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, _), (out, _)) =>
      out <> in
    }
  }
}

object BundleBridgeToAPB {
  def apply(masterParams: APBMasterPortParameters)(implicit p: Parameters): BundleBridgeToAPBNode = {
    val converter = LazyModule(new BundleBridgeToAPB(masterParams))
    converter.node
  }
  def apply(masterParams: APBMasterParameters)(implicit p: Parameters): BundleBridgeToAPBNode = {
    apply(APBMasterPortParameters(Seq(masterParams)))
  }
}
