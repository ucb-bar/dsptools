// See LICENSE for license details.

package dspblocks

import chisel3._
import chisel3.util.RegEnable

object ShiftRegisterWithReset
{
  /** Returns the n-cycle delayed version of the input signal.
    *
    * @param in input to delay
    * @param n number of cycles to delay
    * @param en enable the shift
    */
  def apply[T <: Data](in: T, n: Int, reset: T, en: Bool = true.B): T = {
    // The order of tests reflects the expected use cases.
    if (n != 0) {
      RegEnable(apply(in, n-1, reset, en), reset, en)
    } else {
      in
    }
  }
}
