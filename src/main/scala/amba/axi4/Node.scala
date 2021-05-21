package freechips.rocketchip.amba.axi4

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._

object AXI4BundleBridgeImp extends BundleBridgeImp[AXI4Bundle]

case class AXI4ToBundleBridgeNode(slaveParams: AXI4SlavePortParameters)(implicit valName: ValName)
  extends MixedAdapterNode(AXI4Imp, AXI4BundleBridgeImp)(
    dFn = { masterParams =>
      BundleBridgeParams(() => AXI4Bundle(AXI4BundleParameters(masterParams, slaveParams)))
    },
    uFn = { mp => slaveParams }
)

object AXI4ToBundleBridgeNode {
  def apply(slaveParams: AXI4SlaveParameters, beatBytes: Int)(implicit  valName: ValName): AXI4ToBundleBridgeNode =
    new AXI4ToBundleBridgeNode(AXI4SlavePortParameters(Seq(slaveParams), beatBytes))
}

class AXI4ToBundleBridge(slaveParams: AXI4SlavePortParameters)(implicit p: Parameters) extends LazyModule {
  val node = AXI4ToBundleBridgeNode(slaveParams)
  lazy val module = new LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, _), (out, _)) =>
      out <> in
    }
  }
}

object AXI4ToBundleBridge {
  def apply(slaveParams: AXI4SlavePortParameters)(implicit p: Parameters): AXI4ToBundleBridgeNode = {
    val converter = LazyModule(new AXI4ToBundleBridge(slaveParams))
    converter.node
  }
  def apply(slaveParams: AXI4SlaveParameters, beatBytes: Int)(implicit p: Parameters): AXI4ToBundleBridgeNode = {
    apply(AXI4SlavePortParameters(Seq(slaveParams), beatBytes))
  }
}

case class BundleBridgeToAXI4Node(masterParams: AXI4MasterPortParameters)(implicit valName: ValName)
  extends MixedAdapterNode(AXI4BundleBridgeImp, AXI4Imp)(
    dFn = { mp =>
      masterParams
    },
    uFn = { slaveParams => BundleBridgeParams(None) }
  )

object BundleBridgeToAXI4Node {
  def apply(masterParams: AXI4MasterParameters)(implicit valName: ValName): BundleBridgeToAXI4Node = {
    BundleBridgeToAXI4Node(AXI4MasterPortParameters(Seq(masterParams)))
  }
}

class BundleBridgeToAXI4(masterParams: AXI4MasterPortParameters)(implicit p: Parameters) extends LazyModule {
  val node = BundleBridgeToAXI4Node(masterParams)
  lazy val module = new LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, _), (out, _)) =>
      out <> in
    }
  }
}

object BundleBridgeToAXI4 {
  def apply(masterParams: AXI4MasterPortParameters)(implicit p: Parameters): BundleBridgeToAXI4Node = {
    val converter = LazyModule(new BundleBridgeToAXI4(masterParams))
    converter.node
  }
  def apply(masterParams: AXI4MasterParameters)(implicit p: Parameters): BundleBridgeToAXI4Node = {
    apply(AXI4MasterPortParameters(Seq(masterParams)))
  }
}
