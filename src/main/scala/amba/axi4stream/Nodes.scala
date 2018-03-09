package freechips.rocketchip.amba.axi4stream

import chisel3.internal.sourceinfo.SourceInfo
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._

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
