package freechips.rocketchip.amba.axi4stream

import chisel3._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import scala.math.max

case class AXI4StreamSlaveParameters(
  bundleParams: AXI4StreamBundleParameters = AXI4StreamBundleParameters(8),
  nodePath: Seq[BaseNode] = Seq(),
  alwaysReady: Boolean = false,
  interleavedIdDest: Option[Int] = None)
{
  interleavedIdDest.foreach { x => require(x >= 0) }

  val name: String = nodePath.lastOption.map(_.lazyModule.name).getOrElse("disconnected")
}

case class AXI4StreamSlavePortParameters(
  slaves: Seq[AXI4StreamSlaveParameters])
{
  val bundleParameters: AXI4StreamBundleParameters = AXI4StreamBundleParameters.union(slaves.map(_.bundleParams))
}

case class AXI4StreamMasterParameters(
  name: String,
  bundleParams: AXI4StreamBundleParameters = AXI4StreamBundleParameters(8),
  interleavedIdDest: Option[Int] = None,
  nodePath: Seq[BaseNode] = Seq())
{
  interleavedIdDest.foreach { x => require(x >= 0) }
}

case class AXI4StreamMasterPortParameters(
  masters: Seq[AXI4StreamMasterParameters])
{
  val bundleParameters: AXI4StreamBundleParameters = AXI4StreamBundleParameters.union(masters.map(_.bundleParams))
}

case class AXI4StreamBundleParameters(
  n: Int,
  i: Int = 0,
  d: Int = 0,
  u: Int = 0,
  hasData: Boolean = true,
  hasStrb: Boolean = false,
  hasKeep: Boolean = false)
{
  require (n >= 0, s"AXI4Stream data bytes must be non-negative (got $n)")
  require (i >= 0, s"AXI4Stream id bits must be non-negative (got $i)")
  require (d >= 0, s"AXI4Stream dest bits must be non-negative (got $d)")
  require (u >= 0, s"AXI4Stream user bits must be non-negative (got $u)")

  val dataBits: Int = if (hasData) 8 * n else 0
  val strbBits: Int = if (hasStrb) n     else 0
  val keepBits: Int = if (hasKeep) n     else 0

  def union(x: AXI4StreamBundleParameters) =
    AXI4StreamBundleParameters(
      max(n, x.n),
      max(i, x.i),
      max(d, x.d),
      max(u, x.u),
      hasData || x.hasData,
      hasStrb || x.hasStrb,
      hasKeep || x.hasKeep)
}

//noinspection RedundantDefaultArgument
object AXI4StreamBundleParameters
{
  val emptyBundleParameters = AXI4StreamBundleParameters(
    0,
    0,
    0,
    0,
    hasData = false,
    hasStrb = false,
    hasKeep = false)

  def union(x: Seq[AXI4StreamBundleParameters]): AXI4StreamBundleParameters = x.foldLeft(emptyBundleParameters)((x, y) => x.union(y))

  def joinEdge(master: AXI4StreamMasterPortParameters, slave: AXI4StreamSlavePortParameters): AXI4StreamBundleParameters =
    master.bundleParameters.union(slave.bundleParameters)

}

case class AXI4StreamEdgeParameters(
  master: AXI4StreamMasterPortParameters,
  slave:  AXI4StreamSlavePortParameters)
{
  val bundle: AXI4StreamBundleParameters = AXI4StreamBundleParameters.joinEdge(master, slave)
}
