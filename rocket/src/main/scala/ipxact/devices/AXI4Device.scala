// See LICENSE for license details.

package ipxact.devices

object AXI4Device extends Device {

  // Assumes you use NastiIO Chisel bundle for AXI4
  val portMap: Seq[(String, String)] = {
    Seq(
      "clock"             -> "AC",
      "reset"             -> "AR",
      "_ar_valid"         -> "ARVALID",
      "_ar_ready"         -> "ARREADY",
      "_ar_bits_id"       -> "ARID",
      "_ar_bits_addr"     -> "ARADDR",
      "_ar_bits_size"     -> "ARSIZE",
      "_ar_bits_len"      -> "ARLEN",
      "_ar_bits_burst"    -> "ARBURST",
      "_ar_bits_prot"     -> "ARPROT",
      "_ar_bits_lock"     -> "ARLOCK",
      "_ar_bits_qos"      -> "ARQOS",
      "_ar_bits_region"   -> "ARREGION",
      "_ar_bits_cache"    -> "ARCACHE",
      "_ar_bits_user"     -> "ARUSER",
      "_aw_valid"         -> "AWVALID",
      "_aw_ready"         -> "AWREADY",
      "_aw_bits_id"       -> "AWID",
      "_aw_bits_addr"     -> "AWADDR",
      "_aw_bits_size"     -> "AWSIZE",
      "_aw_bits_len"      -> "AWLEN",
      "_aw_bits_burst"    -> "AWBURST",
      "_aw_bits_prot"     -> "AWPROT",
      "_aw_bits_lock"     -> "AWLOCK",
      "_aw_bits_qos"      -> "AWQOS",
      "_aw_bits_region"   -> "AWREGION",
      "_aw_bits_cache"    -> "AWCACHE",
      "_aw_bits_user"     -> "AWUSER",
      "_w_valid"          -> "WVALID",
      "_w_ready"          -> "WREADY",
      "_w_bits_data"      -> "WDATA",
      "_w_bits_strb"      -> "WSTRB",
      "_w_bits_last"      -> "WLAST",
      "_w_bits_user"      -> "WUSER",
      "_r_valid"          -> "RVALID",
      "_r_ready"          -> "RREADY",
      "_r_bits_id"        -> "RID",
      "_r_bits_resp"      -> "RRESP",
      "_r_bits_data"      -> "RDATA",
      "_r_bits_last"      -> "RLAST",
      "_r_bits_user"      -> "RUSER",
      "_b_valid"          -> "BVALID",
      "_b_ready"          -> "BREADY",
      "_b_bits_id"        -> "BID",
      "_b_bits_resp"      -> "BRESP",
      "_b_bits_user"      -> "BUSER"
    )
  }
}
