package freechips.rocketchip.amba.axi4stream

import chisel3._
import chisel3.internal.sourceinfo.SourceInfo
import chisel3.util.log2Ceil
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._

import scala.math.max

case class AXI4StreamSlaveParameters(
  numEndpoints: Int = 1,
  hasData: Boolean = true,
  hasStrb: Boolean = false,
  hasKeep: Boolean = false,
  nodePath: Seq[BaseNode] = Seq(),
  alwaysReady: Boolean = false,
  interleavedIdDest: Option[Int] = None)
{
  require(numEndpoints >= 1)
  interleavedIdDest.foreach { x => require(x >= 0) }

  def union(in: AXI4StreamSlaveParameters):AXI4StreamSlaveParameters =
    AXI4StreamSlaveParameters(
      numEndpoints + in.numEndpoints,
      hasData || in.hasData,
      hasStrb || in.hasStrb,
      hasKeep || in.hasKeep,
      nodePath ++ in.nodePath,
      alwaysReady && in.alwaysReady,
      (interleavedIdDest, in.interleavedIdDest) match {
        case (Some(mine), Some(theirs)) => Some(mine min theirs)
        case (Some(mine), _)            => Some(mine)
        case (_         , Some(theirs)) => Some(theirs)
        case _                          => None
      }
  )

  val name: String = nodePath.lastOption.map(_.lazyModule.name).getOrElse("disconnected")
}

case class AXI4StreamSlavePortParameters(
  slaves: Seq[AXI4StreamSlaveParameters] = Seq(AXI4StreamSlaveParameters()))
{
  val slaveParams: AXI4StreamSlaveParameters = slaves.reduce((x, y) => x.union(y))
}

case class AXI4StreamMasterParameters(
  name: String = "",
  n: Int = 8,
  u: Int = 0,
  numMasters: Int = 1,
  nodePath: Seq[BaseNode] = Seq())
{
  require(n >= 0)
  require(u >= 0)
  require(numMasters >= 1)

  def union(in: AXI4StreamMasterParameters): AXI4StreamMasterParameters = {
    AXI4StreamMasterParameters(
      name + "|" + in.name,
      n max in.n,
      u max in.u,
      numMasters + in.numMasters,
      nodePath ++ in.nodePath
    )
  }
}

case class AXI4StreamMasterPortParameters(
  masters: Seq[AXI4StreamMasterParameters] = Seq(AXI4StreamMasterParameters()))
{
  val masterParams: AXI4StreamMasterParameters = masters.reduce((x, y) => x.union(y))
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

  def joinEdge(master: AXI4StreamMasterPortParameters, slave: AXI4StreamSlavePortParameters): AXI4StreamBundleParameters = {
    val m = master.masterParams
    val s = slave.slaveParams
    AXI4StreamBundleParameters(
      m.n,
      log2Ceil(m.numMasters),
      log2Ceil(s.numEndpoints),
      m.u,
      s.hasData,
      s.hasStrb,
      s.hasKeep)
  }

}

case class AXI4StreamEdgeParameters(
  master: AXI4StreamMasterPortParameters,
  slave:  AXI4StreamSlavePortParameters,
  p: Parameters,
  sourceInfo: SourceInfo)
{
  val bundle: AXI4StreamBundleParameters = AXI4StreamBundleParameters.joinEdge(master, slave)
}
