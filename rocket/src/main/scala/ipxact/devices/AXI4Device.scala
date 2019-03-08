// See LICENSE for license details.

package ipxact.devices

object AXI4Device extends Device {

  // Assumes you use NastiIO Chisel bundle for AXI4
  def getPortMap(prefix: String): Seq[(String, String)] = {
    Seq(
      "clock"                       -> "AC",
      "reset"                       -> "AR",
      s"${prefix}_ar_valid"         -> "ARVALID",
      s"${prefix}_ar_ready"         -> "ARREADY",
      s"${prefix}_ar_bits_id"       -> "ARID",
      s"${prefix}_ar_bits_addr"     -> "ARADDR",
      s"${prefix}_ar_bits_size"     -> "ARSIZE",
      s"${prefix}_ar_bits_len"      -> "ARLEN",
      s"${prefix}_ar_bits_burst"    -> "ARBURST",
      s"${prefix}_ar_bits_prot"     -> "ARPROT",
      s"${prefix}_ar_bits_lock"     -> "ARLOCK",
      s"${prefix}_ar_bits_qos"      -> "ARQOS",
      s"${prefix}_ar_bits_region"   -> "ARREGION",
      s"${prefix}_ar_bits_cache"    -> "ARCACHE",
      s"${prefix}_ar_bits_user"     -> "ARUSER",
      s"${prefix}_aw_valid"         -> "AWVALID",
      s"${prefix}_aw_ready"         -> "AWREADY",
      s"${prefix}_aw_bits_id"       -> "AWID",
      s"${prefix}_aw_bits_addr"     -> "AWADDR",
      s"${prefix}_aw_bits_size"     -> "AWSIZE",
      s"${prefix}_aw_bits_len"      -> "AWLEN",
      s"${prefix}_aw_bits_burst"    -> "AWBURST",
      s"${prefix}_aw_bits_prot"     -> "AWPROT",
      s"${prefix}_aw_bits_lock"     -> "AWLOCK",
      s"${prefix}_aw_bits_qos"      -> "AWQOS",
      s"${prefix}_aw_bits_region"   -> "AWREGION",
      s"${prefix}_aw_bits_cache"    -> "AWCACHE",
      s"${prefix}_aw_bits_user"     -> "AWUSER",
      s"${prefix}_w_valid"          -> "WVALID",
      s"${prefix}_w_ready"          -> "WREADY",
      s"${prefix}_w_bits_data"      -> "WDATA",
      s"${prefix}_w_bits_strb"      -> "WSTRB",
      s"${prefix}_w_bits_last"      -> "WLAST",
      s"${prefix}_w_bits_user"      -> "WUSER",
      s"${prefix}_r_valid"          -> "RVALID",
      s"${prefix}_r_ready"          -> "RREADY",
      s"${prefix}_r_bits_id"        -> "RID",
      s"${prefix}_r_bits_resp"      -> "RRESP",
      s"${prefix}_r_bits_data"      -> "RDATA",
      s"${prefix}_r_bits_last"      -> "RLAST",
      s"${prefix}_r_bits_user"      -> "RUSER",
      s"${prefix}_b_valid"          -> "BVALID",
      s"${prefix}_b_ready"          -> "BREADY",
      s"${prefix}_b_bits_id"        -> "BID",
      s"${prefix}_b_bits_resp"      -> "BRESP",
      s"${prefix}_b_bits_user"      -> "BUSER"
    )
  }
}
