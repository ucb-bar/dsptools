package freechips.rocketchip.amba.axi4stream

import chisel3._
import chisel3.internal.sourceinfo.SourceInfo
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._

object AXI4StreamImp extends NodeImp[AXI4StreamMasterPortParameters, AXI4StreamSlavePortParameters, AXI4StreamEdgeParameters, AXI4StreamEdgeParameters, AXI4StreamBundle]
{
  def edgeO(pd: AXI4StreamMasterPortParameters, pu: AXI4StreamSlavePortParameters): AXI4StreamEdgeParameters = AXI4StreamEdgeParameters(pd, pu)
  def edgeI(pd: AXI4StreamMasterPortParameters, pu: AXI4StreamSlavePortParameters): AXI4StreamEdgeParameters = AXI4StreamEdgeParameters(pd, pu)

  def bundleO(eo: AXI4StreamEdgeParameters): AXI4StreamBundle = AXI4StreamBundle(eo.bundle)
  def bundleI(ei: AXI4StreamEdgeParameters): AXI4StreamBundle = AXI4StreamBundle(ei.bundle)

  def colour = "#00ccdd"

  override def labelI(ei: AXI4StreamEdgeParameters): String = ei.master.masterParams.name
  override def labelO(eo: AXI4StreamEdgeParameters): String = eo.master.masterParams.name

  override def mixO(pd: AXI4StreamMasterPortParameters, node: OutwardNode[AXI4StreamMasterPortParameters, AXI4StreamSlavePortParameters, AXI4StreamBundle]): AXI4StreamMasterPortParameters =
    pd.copy(masters = pd.masters.map { c => c.copy (nodePath = node +: c.nodePath) })
  override def mixI(pu: AXI4StreamSlavePortParameters, node: InwardNode[AXI4StreamMasterPortParameters, AXI4StreamSlavePortParameters, AXI4StreamBundle]): AXI4StreamSlavePortParameters =
    pu.copy(slaves = pu.slaves.map { m => m.copy (nodePath = node +: m.nodePath) })
}

case class AXI4StreamIdentityNode() extends IdentityNode(AXI4StreamImp)
case class AXI4StreamMasterNode(portParams: Seq[AXI4StreamMasterPortParameters]) extends SourceNode(AXI4StreamImp)(portParams)
case class AXI4StreamSlaveNode(portParams: Seq[AXI4StreamSlavePortParameters]) extends SinkNode(AXI4StreamImp)(portParams)
case class AXI4StreamAdapterNode(
  masterFn: AXI4StreamMasterPortParameters => AXI4StreamMasterPortParameters,
  slaveFn:  AXI4StreamSlavePortParameters  => AXI4StreamSlavePortParameters,
  numPorts: Range.Inclusive = 0 to 999)
  extends AdapterNode(AXI4StreamImp)(masterFn, slaveFn, numPorts)

object AXI4StreamAdapterNode {
  def widthAdapter(in: AXI4StreamMasterPortParameters, dataWidthConversion: Int => Int): AXI4StreamMasterPortParameters = {
    val masters = in.masters
    val newMasters = masters.map { case m =>
        val n = m.n
        m.copy(n = dataWidthConversion(n))
    }
    AXI4StreamMasterPortParameters(newMasters)
  }
}

case class AXI4StreamOutputNode() extends OutputNode(AXI4StreamImp)
case class AXI4StreamInputNode() extends InputNode(AXI4StreamImp)

case class AXI4StreamBlindOutputNode(portParams: Seq[AXI4StreamSlavePortParameters]) extends BlindOutputNode(AXI4StreamImp)(portParams)
case class AXI4StreamBlindInputNode(portParams: Seq[AXI4StreamMasterPortParameters]) extends BlindInputNode(AXI4StreamImp)(portParams)

case class AXI4StreamInternalOutputNode(portParams: Seq[AXI4StreamSlavePortParameters]) extends InternalOutputNode(AXI4StreamImp)(portParams)
case class AXI4StreamInternalInputNode(portParams: Seq[AXI4StreamMasterPortParameters]) extends InternalInputNode(AXI4StreamImp)(portParams)
