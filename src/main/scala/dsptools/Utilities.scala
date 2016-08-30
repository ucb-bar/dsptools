// See LICENSE for license details.

package dsptools

import chisel3.{Bool, UInt}

object Utilities {

}

/** Short [UInt] Mod (x,n,[optional] dly)
  * Returns (x % n) for x <= 2n-1
  * & overflow flag set when (x >= n)
  * dly: output delay
  */
object Mod {
  def apply(x: UInt, n: UInt):          (UInt,Bool) = apply(x,n,0)
  def apply(x: UInt, n: UInt, dly:Int): (UInt,Bool) = {
    val result = x % n
    (result, x >= n)
//
//    val nmax = n.getRange.max
//    val xmax = x.getRange.max
//    val xValidMax = 2*nmax-1
//    if (xmax > xValidMax)
//      Error("Cannot use short mod x%n for given range of inputs. x <= 2n-1 required."
//        + " (x,n) max --> (" + xmax + "," + nmax + ")")
//    if (xmax < nmax) {
//      n.doNothing()
//      (x.pipe(dly),Bool(false))                                            // No mod needed when x < n
//    }
//    else {
//      val newx = x.lengthen(xValidMax)
//      val diff = (newx - n).pipe(dly)                                         // No FPGA retiming issue @ carry chain
//      if (diff.getWidth != newx.getWidth)
//        Error ("Sign bit location after subtraction is unexpected in Mod.")
//      val nOF  = diff.extract(newx.getWidth-1)                                // x >= n -> mod = x-n; else mod = x
//      val mod = Mux(nOF,x.pipe(dly),diff)
//      (mod.shorten(nmax-1),!nOF)
//    }
  }
}
