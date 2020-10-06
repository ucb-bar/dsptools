// SPDX-License-Identifier: Apache-2.0

package dsptools.counters

import chisel3._

object CounterWithReset {
  def apply(cond: Bool, n: Int, reset: Bool): (UInt, Bool) = {
    val c = chisel3.util.Counter(cond, n)
    if (n > 1) { when (reset) { c._1 := 0.U } }
    c
  }
}
