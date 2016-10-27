// See LICENSE for license details.

package dsptools.counters

import chisel3._

object CounterWithReset { 
  def apply(cond: Bool, n: Int, reset: Bool) = {
    val c = Counter(cond, n)
    if (n > 1) { when (reset) { c._1 := UInt(0) } }
    c
  }
}
