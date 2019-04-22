package freechips.rocketchip.amba

import freechips.rocketchip.diplomacy._

package object axi4stream {
  type AXI4StreamInwardNode = InwardNodeHandle[AXI4StreamMasterPortParameters, AXI4StreamSlavePortParameters,
    AXI4StreamEdgeParameters, AXI4StreamBundle]
  type AXI4StreamOutwardNode = OutwardNodeHandle[AXI4StreamMasterPortParameters, AXI4StreamSlavePortParameters,
    AXI4StreamEdgeParameters, AXI4StreamBundle]
  type AXI4StreamNode = MixedNode[
    AXI4StreamMasterPortParameters, AXI4StreamSlavePortParameters, AXI4StreamEdgeParameters, AXI4StreamBundle,
    AXI4StreamMasterPortParameters, AXI4StreamSlavePortParameters, AXI4StreamEdgeParameters, AXI4StreamBundle]
  type AXI4StreamNodeHandle = NodeHandle[
    AXI4StreamMasterPortParameters, AXI4StreamSlavePortParameters, AXI4StreamEdgeParameters, AXI4StreamBundle,
    AXI4StreamMasterPortParameters, AXI4StreamSlavePortParameters, AXI4StreamEdgeParameters, AXI4StreamBundle]

  implicit class AXI4StreamClockDomainCrossing(val x: HasClockDomainCrossing) extends AnyVal {
    def crossIn(n: AXI4StreamInwardNode)(implicit valName: ValName) =
      AXI4StreamInwardCrossingHelper(valName.name, x, n)
    def crossOut(n: AXI4StreamOutwardNode)(implicit valName: ValName) =
      AXI4StreamOutwardCrossingHelper(valName.name, x, n)
    def cross(n: AXI4StreamInwardNode)(implicit valName: ValName) = crossIn(n)
    def cross(n: AXI4StreamOutwardNode)(implicit valName: ValName) = crossOut(n)
  }
}
