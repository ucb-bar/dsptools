// See LICENSE for license details.

package dspblocks

import chisel3._

object CounterWithReset {
  def apply(cond: Bool, n: Int, sync_reset: Bool, comb_reset: Bool = false.B): (UInt, Bool) = {
    val c = chisel3.util.Counter(cond, n)
    val out = Wire(UInt())
    out := c._1
    if (n > 1) {
      when (sync_reset) { c._1 := 0.U }
      when (comb_reset) { c._1 := 1.U; out := 0.U }
    }
    (out, c._2)
  }
}
