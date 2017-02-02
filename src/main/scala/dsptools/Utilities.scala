// See LICENSE for license details.

package dsptools

import chisel3._
import chisel3.experimental.FixedPoint
import dsptools.numbers.DspReal
import spire.algebra.Ring

object Utilities {
 
  // [stevo]: converts negative values to their 2's complement version
  // also works for positive numbers
  // sign extends to totalWidth
  // used for FixedPoint
  def toBigIntUnsigned(x: Double, totalWidth: Int, fractionalWidth: Int): BigInt = {
    var bi = FixedPoint.toBigInt(x, fractionalWidth)
    // TODO: doesn't work for totalWidth > 32
    require(bi < BigInt.apply(math.pow(2, totalWidth-1).toInt) && bi >= BigInt.apply(-math.pow(2, totalWidth-1).toInt), s"Error: cannot represent value $x as a FixedPoint of width $totalWidth and binary point $fractionalWidth")
    if (bi < 0) {
      bi = -bi
      (0 until totalWidth).foreach { x => bi = bi.flipBit(x) }
      bi = bi + 1
    }
    bi
  }

  // [stevo]: converts a positive 2's complement BigInt to a double (positive or negative, where the MSB is the sign bit)
  // used for FixedPoint
  def toDoubleFromUnsigned(i: BigInt, totalWidth: Int, fractionalWidth: Int): Double = {
    require(i >= 0, "Error: attempting to convert a signed BigInt to a double, did you mean toDouble instead?") 
    var j = i
    if (i.testBit(totalWidth-1)) { 
      (0 until totalWidth).foreach { x => j = j.flipBit(x) }
      j = -(j + 1)
    }
    FixedPoint.toDouble(j, fractionalWidth)
  }

  def doubleToBigIntBits(double: Double): BigInt = {
    val ret = BigInt(java.lang.Double.doubleToLongBits(double))
    if(ret >= 0) { 
      ret 
    } else  {
      DspReal.bigInt2powUnderlying + ret
    }
  }

  def bigIntBitsToDouble(bigInt: BigInt): Double = {
    java.lang.Double.longBitsToDouble(bigInt.toLong)
  }


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
