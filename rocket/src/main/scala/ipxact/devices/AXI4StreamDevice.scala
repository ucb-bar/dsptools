// See LICENSE for license details.

package ipxact.devices

object AXI4StreamDevice extends Device {

  // Assumes you use ValidWithSync Chisel bundle for AXI4-Stream
  val portMap: Seq[(String, String)] = {
    Seq(
      "clock"      -> "ACLK",
      "reset"      -> "ARESETn",
      "_valid"     -> "TVALID",
      "_sync"      -> "TLAST",
      "_bits"      -> "TDATA"
    )
  }
}
