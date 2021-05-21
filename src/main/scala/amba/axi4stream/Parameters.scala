package freechips.rocketchip.amba.axi4stream

import chisel3.internal.sourceinfo.SourceInfo
import chisel3.util.log2Ceil
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util.AsyncQueueParams

import scala.math.max

/**
  * Parameters case class for AXI4 Stream bundles (slave side)
  * @param numEndpoints number of endpoints for the purposes of the dest field
  * @param hasData bundle includes the data field
  * @param hasStrb bundle includes the strobe field
  * @param hasKeep bundle includes the keep field
  * @param nodePath path of nodes that got us here
  * @param alwaysReady this interface is always ready when reset is not asserted
  * @param interleavedIdDest maximum number of transactions with unique
  *                          id/dest pairs that can be in flight at once
  */
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

  /**
    * Combine two parameters objects. If a field is present in either, it is present
    * in the output. The number of endpoints is the sum. For interleavedIdDest, find the min.
    * @param in parameters for another slave
    * @return
    */
  def union(in: AXI4StreamSlaveParameters):AXI4StreamSlaveParameters =
    AXI4StreamSlaveParameters(
      numEndpoints + in.numEndpoints,
      hasData || in.hasData,
      hasStrb || in.hasStrb,
      hasKeep || in.hasKeep,
      // TODO this is bad
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

object AXI4StreamSlavePortParameters {
  def apply(p: AXI4StreamSlaveParameters): AXI4StreamSlavePortParameters = {
    AXI4StreamSlavePortParameters(Seq(p))
  }
}

/**
  * Parameters case class for AXI4 Stream bundles (master side)
  * @param name Name of master
  * @param n sets width of data, strb, keep, etc.
  * @param u sets width of user
  * @param numMasters number of entry points for purposes of id
  * @param nodePath path of nodes that got us here
  */
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

  /**
    * Combine two parameters objects. Choose the max widths and add the number of masters.
    * @param in parameters to be merged with
    * @return
    */
  def union(in: AXI4StreamMasterParameters): AXI4StreamMasterParameters = {
    AXI4StreamMasterParameters(
      name + "|" + in.name,
      n max in.n,
      u max in.u,
      numMasters + in.numMasters,
      // TODO this is bad
      nodePath ++ in.nodePath
    )
  }
}

case class AXI4StreamMasterPortParameters(
  masters: Seq[AXI4StreamMasterParameters] = Seq(AXI4StreamMasterParameters()))
{
  val masterParams: AXI4StreamMasterParameters = masters.reduce((x, y) => x.union(y))
}

object AXI4StreamMasterPortParameters {
  def apply(p: AXI4StreamMasterParameters): AXI4StreamMasterPortParameters = {
    AXI4StreamMasterPortParameters(Seq(p))
  }
}

/**
  * Parameters case class for AXI4 Stream bundles
  * @param n sets width of data, strb, keep, etc.
  * @param i sets width of id field
  * @param d sets width of dest field
  * @param u sets width of user field
  * @param hasData bundle includes the data field
  * @param hasStrb bundle includes the strobe field
  * @param hasKeep bundle includes the keep field
  */
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

  /**
    * Combine two parameters objects. Choose max of widths. If a field is present in
    * either object, include it in the combined object.
    * @param x parameters to be merged with
    * @return
    */
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

object AXI4StreamBundleParameters
{
  /**
    * Parameters for bundle with no fields
    */
  val emptyBundleParameters = AXI4StreamBundleParameters(
    n = 0, i = 0, d = 0, u =0,
    hasData = false, hasStrb = false, hasKeep = false)

  /**
    * Combine master and slave port parameters.
    * Set id width to number of bits needed for numMasters and dest width to number of bits needed for numEndpoints
    * @param master master port parameters
    * @param slave slave port parameters
    * @return
    */
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

case class AXI4StreamAsyncSlavePortParameters(async: AsyncQueueParams, base: AXI4StreamSlavePortParameters)
object AXI4StreamAsyncSlavePortParameters {
  def apply(async: AsyncQueueParams, base: AXI4StreamSlaveParameters): AXI4StreamAsyncSlavePortParameters =
    AXI4StreamAsyncSlavePortParameters(async, AXI4StreamSlavePortParameters(Seq(base)))
}
case class AXI4StreamAsyncMasterPortParameters(base: AXI4StreamMasterPortParameters)
object AXI4StreamAsyncMasterPortParameters {
  def apply(base: AXI4StreamMasterParameters): AXI4StreamAsyncMasterPortParameters =
    AXI4StreamAsyncMasterPortParameters(AXI4StreamMasterPortParameters(Seq(base)))
}

case class AXI4StreamAsyncBundleParameters(async: AsyncQueueParams, base: AXI4StreamBundleParameters)
case class AXI4StreamAsyncEdgeParameters
(
  master: AXI4StreamAsyncMasterPortParameters,
  slave: AXI4StreamAsyncSlavePortParameters,
  params: Parameters,
  sourceInfo: SourceInfo
) {
  val bundle =
    AXI4StreamAsyncBundleParameters(slave.async, AXI4StreamBundleParameters.joinEdge(master.base, slave.base))
}

case class AXI4StreamBufferParams(p: BufferParams = BufferParams.none) extends DirectedBuffers[AXI4StreamBufferParams] {
  // no channels in
  override def copyIn(x: BufferParams): AXI4StreamBufferParams = this.copy()

  // only one channel, and it is out
  override def copyOut(x: BufferParams): AXI4StreamBufferParams = this.copy(p = x)

  override def copyInOut(x: BufferParams): AXI4StreamBufferParams = this.copyIn(x).copyOut(x)
}