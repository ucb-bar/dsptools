package freechips.rocketchip.amba

import freechips.rocketchip.diplomacy.{MixedNode, NodeHandle}

package object axi4stream {
  type AXI4StreamNode = MixedNode[
    AXI4StreamMasterPortParameters, AXI4StreamSlavePortParameters, AXI4StreamEdgeParameters, AXI4StreamBundle,
    AXI4StreamMasterPortParameters, AXI4StreamSlavePortParameters, AXI4StreamEdgeParameters, AXI4StreamBundle]
  type AXI4StreamNodeHandle = NodeHandle[
    AXI4StreamMasterPortParameters, AXI4StreamSlavePortParameters, AXI4StreamEdgeParameters, AXI4StreamBundle,
    AXI4StreamMasterPortParameters, AXI4StreamSlavePortParameters, AXI4StreamEdgeParameters, AXI4StreamBundle]

}
