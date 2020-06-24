package freechips.rocketchip.interrupts

import chisel3._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._

object IntBundleBridgeImp extends BundleBridgeImp[Vec[Bool]]

case class IntToBundleBridgeNode(sinkParams: IntSinkPortParameters)(implicit valName: ValName)
extends MixedAdapterNode(IntImp, IntBundleBridgeImp)(
  dFn = { sourceParams =>
    BundleBridgeParams(() => Vec(sinkParams.sinks.length, Bool()))
  },
  uFn = { sp => sinkParams }
)

class IntToBundleBridge(slaveParams: IntSinkPortParameters)(implicit p: Parameters)
extends LazyModule {
  val node = IntToBundleBridgeNode(slaveParams)

  lazy val module = new LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, _), (out, _)) =>
      out := in
    }
  }
}

object IntToBundleBridge {
  def apply(sinkParams: IntSinkPortParameters)(implicit p: Parameters): IntToBundleBridgeNode =
  {
    val converter = LazyModule(new IntToBundleBridge(sinkParams))
    converter.node
  }
}

case class BundleBridgeToIntNode(sourceParams: IntSourcePortParameters)
(implicit valName: ValName) extends MixedAdapterNode(IntBundleBridgeImp, IntImp)(
  dFn = {sinkParams => sourceParams},
  uFn = { slaveParams => BundleBridgeParams(None) }
)

class BundleBridgeToInt(sourceParams: IntSourcePortParameters)(implicit p: Parameters)
extends LazyModule {
  val node = BundleBridgeToIntNode(sourceParams)
  lazy val module = new LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, _), (out, _)) =>
      out := in
    }
  }
}

object BundleBridgeToInt {
  def apply(sourceParams: IntSourcePortParameters)(implicit p: Parameters): BundleBridgeToIntNode =
  {
    val converter = LazyModule(new BundleBridgeToInt(sourceParams))
    converter.node
  }
}
