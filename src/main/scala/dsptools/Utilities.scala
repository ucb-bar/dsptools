// See LICENSE for license details.

package dsptools

import chisel3._
import dsptools.numbers.DspReal
import spire.algebra.Ring

object Utilities {
  //Todo: I'm very dissatisfied with the name removeMantissa
  def removeMatissa[T <: Data:Ring](number: T, trim: TrimType): T = {
    number match {
      case dspReal: DspReal=>
        trim match {
          case Truncate => dspReal.intPart().asInstanceOf[T]
          case Round => (dspReal + DspReal(0.5)).intPart().asInstanceOf[T]
          case _ => throw DspException(s"removeMantissa: unsupported trim operation $trim on $dspReal")
        }
      case fixedPoint: FixedPoint =>
        trim match {
          case Truncate => fixedPoint.asInstanceOf[T]//.setBinaryPoint(0).asInstanceOf[T]
          case Round => (fixedPoint + FixedPoint.fromDouble(0.5, binaryPoint = 1)).asInstanceOf[T]//.setBinaryPoint(0).asInstanceOf[T]
          case _ => throw DspException(s"removeMantissa: unsupported trim operation $trim on $fixedPoint")
        }
      case _ => throw DspException(s"removeMantissa: unsupported number type $number")
    }
  }

  def isOdd[T <: Data:Ring](number: T, trim: TrimType = Truncate): Bool = {
    (removeMatissa(number, trim).asUInt() & 1.U) === 1.U
  }

  def isEven[T <: Data:Ring](number: T, trim: TrimType = Truncate): Bool = ! isEven(number, trim)

  def doubleToBigIntBits(double: Double): BigInt = {
    if (double < 0) {
      BigInt(java.lang.Double.doubleToLongBits(-double))+BigInt(1<<30)*BigInt(1<<30)*BigInt(8)
    } else {
      BigInt(java.lang.Double.doubleToLongBits(double))
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
