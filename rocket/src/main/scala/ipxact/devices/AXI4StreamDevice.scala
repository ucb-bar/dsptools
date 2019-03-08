// See LICENSE for license details.

package ipxact.devices

object AXI4StreamDevice extends Device {

  // Assumes you use ValidWithSync Chisel bundle for AXI4-Stream
  def getPortMap(prefix: String): Seq[(String, String)] = {
    Seq(
      "clock"            -> "ACLK",
      "reset"            -> "ARESETn",
      s"${prefix}_valid" -> "TVALID",
      s"${prefix}_sync"  -> "TLAST",
      s"${prefix}_bits"  -> "TDATA"
    )
  }
}
