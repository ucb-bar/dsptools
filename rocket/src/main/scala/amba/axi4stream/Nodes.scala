// See LICENSE for license details

package freechips.rocketchip.amba.axi4stream

import chisel3.internal.sourceinfo.SourceInfo
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util.AsyncQueueParams

/**
  * Implementation of Node for AXI4 Stream
  */
object AXI4StreamImp extends SimpleNodeImp[AXI4StreamMasterPortParameters, AXI4StreamSlavePortParameters, AXI4StreamEdgeParameters, AXI4StreamBundle]
{
  def edge(pd: AXI4StreamMasterPortParameters, pu: AXI4StreamSlavePortParameters, p: Parameters, sourceInfo: SourceInfo): AXI4StreamEdgeParameters = AXI4StreamEdgeParameters(pd, pu, p, sourceInfo)
  def bundle(e: AXI4StreamEdgeParameters): AXI4StreamBundle = AXI4StreamBundle(e.bundle)
  def render(e: AXI4StreamEdgeParameters) = RenderedEdge(colour = "#0033ff", label = e.master.masterParams.n.toString)

  def colour = "#00ccdd"

  override def mixO(pd: AXI4StreamMasterPortParameters, node: OutwardNode[AXI4StreamMasterPortParameters, AXI4StreamSlavePortParameters, AXI4StreamBundle]): AXI4StreamMasterPortParameters =
    pd.copy(masters = pd.masters.map { c => c.copy (nodePath = node +: c.nodePath) })
  override def mixI(pu: AXI4StreamSlavePortParameters, node: InwardNode[AXI4StreamMasterPortParameters, AXI4StreamSlavePortParameters, AXI4StreamBundle]): AXI4StreamSlavePortParameters =
    pu.copy(slaves = pu.slaves.map { m => m.copy (nodePath = node +: m.nodePath) })
}

case class AXI4StreamIdentityNode()(implicit valName: ValName) extends IdentityNode(AXI4StreamImp)()
case class AXI4StreamMasterNode(portParams: Seq[AXI4StreamMasterPortParameters])(implicit valName: ValName) extends SourceNode(AXI4StreamImp)(portParams)
object AXI4StreamMasterNode {
  def apply(p: AXI4StreamMasterPortParameters)(implicit valName: ValName): AXI4StreamMasterNode = {
    AXI4StreamMasterNode(Seq(p))
  }
  def apply(p: AXI4StreamMasterParameters)(implicit valName: ValName): AXI4StreamMasterNode = {
    AXI4StreamMasterNode(AXI4StreamMasterPortParameters(p))
  }
}
case class AXI4StreamSlaveNode(portParams: Seq[AXI4StreamSlavePortParameters])(implicit valName: ValName) extends SinkNode(AXI4StreamImp)(portParams)
object AXI4StreamSlaveNode {
  def apply(p: AXI4StreamSlavePortParameters)(implicit valName: ValName): AXI4StreamSlaveNode = {
    AXI4StreamSlaveNode(Seq(p))
  }
  def apply(p: AXI4StreamSlaveParameters)(implicit valName: ValName): AXI4StreamSlaveNode = {
    AXI4StreamSlaveNode(AXI4StreamSlavePortParameters(p))
  }
}

object AXI4StreamNameNode {
  def apply(name: ValName) = AXI4StreamIdentityNode()(name)
  def apply(name: Option[String]): AXI4StreamIdentityNode = apply(ValName(name.getOrElse("with_no_name")))
  def apply(name: String): AXI4StreamIdentityNode = apply(Some(name))
}

case class AXI4StreamNexusNode(
  masterFn: Seq[AXI4StreamMasterPortParameters] => AXI4StreamMasterPortParameters,
  slaveFn:  Seq[AXI4StreamSlavePortParameters]  => AXI4StreamSlavePortParameters
)(implicit valName: ValName) extends NexusNode(AXI4StreamImp)(masterFn, slaveFn)

case class AXI4StreamAdapterNode(
  masterFn: AXI4StreamMasterPortParameters => AXI4StreamMasterPortParameters,
  slaveFn:  AXI4StreamSlavePortParameters  => AXI4StreamSlavePortParameters)(implicit valName: ValName)
  extends AdapterNode(AXI4StreamImp)(masterFn, slaveFn)

object AXI4StreamAdapterNode {
  def widthAdapter(in: AXI4StreamMasterPortParameters, dataWidthConversion: Int => Int): AXI4StreamMasterPortParameters = {
    val masters = in.masters
    val newMasters = masters.map { m =>
      val n = m.n
      m.copy(n = dataWidthConversion(n))
    }
    AXI4StreamMasterPortParameters(newMasters)
  }
}

object AXI4StreamAsyncImp extends
  SimpleNodeImp[
    AXI4StreamAsyncMasterPortParameters,
    AXI4StreamAsyncSlavePortParameters,
    AXI4StreamAsyncEdgeParameters,
    AXI4StreamAsyncBundle
  ] {
  def edge(
            pd: AXI4StreamAsyncMasterPortParameters,
            pu: AXI4StreamAsyncSlavePortParameters,
            p: Parameters,
            sourceInfo: SourceInfo): AXI4StreamAsyncEdgeParameters = {
     AXI4StreamAsyncEdgeParameters(pd, pu, p, sourceInfo)
  }

  override def bundle(e: AXI4StreamAsyncEdgeParameters): AXI4StreamAsyncBundle =
    AXI4StreamAsyncBundle(e.bundle)

  override def render(e: AXI4StreamAsyncEdgeParameters): RenderedEdge =
    RenderedEdge(colour = "#ffcc00", label = e.slave.async.depth.toString)

  override def mixO(
                     pd: AXI4StreamAsyncMasterPortParameters,
                     node: OutwardNode[
                       AXI4StreamAsyncMasterPortParameters,
                       AXI4StreamAsyncSlavePortParameters,
                       AXI4StreamAsyncBundle]
                   ): AXI4StreamAsyncMasterPortParameters = {
    pd.copy(base = pd.base.copy(masters = pd.base.masters.map { c => c.copy(nodePath = node +: c.nodePath) }))
  }

  override def mixI(
    pu: AXI4StreamAsyncSlavePortParameters,
    node: InwardNode[
      AXI4StreamAsyncMasterPortParameters,
      AXI4StreamAsyncSlavePortParameters,
      AXI4StreamAsyncBundle]
                   ): AXI4StreamAsyncSlavePortParameters = {
    pu.copy(base = pu.base.copy(slaves = pu.base.slaves.map { c => c.copy(nodePath = node +: c.nodePath) }))
  }
}

case class AXI4StreamAsyncSourceNode(sync: Option[Int])(implicit valName: ValName)
  extends MixedAdapterNode(AXI4StreamImp, AXI4StreamAsyncImp)(
    dFn = { p => AXI4StreamAsyncMasterPortParameters(p) },
    uFn = { p => p.base.copy() }
  )

case class AXI4StreamAsyncSinkNode(async: AsyncQueueParams)(implicit valName: ValName)
  extends MixedAdapterNode(AXI4StreamAsyncImp, AXI4StreamImp)(
    dFn = { p => p.base },
    uFn = { p => AXI4StreamAsyncSlavePortParameters(async, p) }
  )

case class AXI4StreamAsyncIdentityNode()(implicit valName: ValName) extends IdentityNode(AXI4StreamAsyncImp)()

object AXI4StreamAsyncNameNode {
  def apply(name: ValName) = AXI4StreamAsyncIdentityNode()(name)
  def apply(name: Option[String]): AXI4StreamAsyncIdentityNode = apply(ValName(name.getOrElse("with_no_name")))
  def apply(name: String): AXI4StreamAsyncIdentityNode = apply(Some(name))
}

object AXI4StreamBundleBridgeImp extends BundleBridgeImp[AXI4StreamBundle]

case class AXI4StreamToBundleBridgeNode(slaveParams: AXI4StreamSlavePortParameters)(implicit valName: ValName)
extends MixedAdapterNode(AXI4StreamImp, AXI4StreamBundleBridgeImp)(
  dFn = { masterParams =>
      BundleBridgeParams(() => AXI4StreamBundle(AXI4StreamBundleParameters.joinEdge(masterParams, slaveParams)))
  },
  uFn = { mp => slaveParams }
)

object AXI4StreamToBundleBridgeNode {
  def apply(slaveParams: AXI4StreamSlaveParameters)(implicit p: Parameters) =
    new AXI4StreamToBundleBridge(AXI4StreamSlavePortParameters(slaveParams))
}

class AXI4StreamToBundleBridge(slaveParams: AXI4StreamSlavePortParameters)(implicit p: Parameters) extends LazyModule {
  val node = AXI4StreamToBundleBridgeNode(slaveParams)

  lazy val module = new LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, _), (out, _)) =>
      out.valid := in.valid
      out.bits := in.bits
      in.ready := out.ready
    }
  }
}

object AXI4StreamToBundleBridge {
  def apply(slaveParams: AXI4StreamSlavePortParameters)(implicit p: Parameters): AXI4StreamToBundleBridgeNode = {
    val converter = LazyModule(new AXI4StreamToBundleBridge(slaveParams))
    converter.node
  }
  def apply(slaveParams: AXI4StreamSlaveParameters)(implicit p: Parameters): AXI4StreamToBundleBridgeNode = {
    apply(AXI4StreamSlavePortParameters(slaveParams))
  }
}

case class BundleBridgeToAXI4StreamNode(masterParams: AXI4StreamMasterPortParameters)(implicit valName: ValName)
extends MixedAdapterNode(AXI4StreamBundleBridgeImp, AXI4StreamImp)(
  dFn = { mp =>
    masterParams
  },
  uFn = { slaveParams => BundleBridgeParams(None) }// BundleBridgeParams(() => AXI4StreamBundle(AXI4StreamBundleParameters.joinEdge(masterParams, slaveParams)))}
)

object BundleBridgeToAXI4StreamNode {
  def apply(masterParams: AXI4StreamMasterParameters)(implicit valName: ValName): BundleBridgeToAXI4StreamNode = {
    BundleBridgeToAXI4StreamNode(AXI4StreamMasterPortParameters(masterParams))
  }
}

class BundleBridgeToAXI4Stream(masterParams: AXI4StreamMasterPortParameters)(implicit p: Parameters) extends LazyModule {
  val node = BundleBridgeToAXI4StreamNode(masterParams)

  lazy val module = new LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, _), (out, _)) =>
        out.valid := in.valid
        out.bits := in.bits
        in.ready := out.ready
    }
  }
}

object BundleBridgeToAXI4Stream {
  def apply(masterParams: AXI4StreamMasterPortParameters)(implicit p: Parameters): BundleBridgeToAXI4StreamNode = {
    val converter = LazyModule(new BundleBridgeToAXI4Stream(masterParams))
    converter.node
  }
  def apply(masterParams: AXI4StreamMasterParameters)(implicit p: Parameters): BundleBridgeToAXI4StreamNode = {
    apply(AXI4StreamMasterPortParameters(masterParams))
  }
}
