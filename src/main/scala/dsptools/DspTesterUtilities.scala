// See LICENSE for license details.

package dsptools

import chisel3._
import chisel3.experimental.FixedPoint
import dsptools.numbers.DspReal
import spire.algebra.Ring

object DspTesterUtilities {
 
  // Converts negative Double's to their 2's complement BigInt equivalents 
  // (totalWidth, fractionalWidth of some FixedPoint)
  def toBigIntUnsigned(x: Double, totalWidth: Int, fractionalWidth: Int): BigInt = {
    val bi = FixedPoint.toBigInt(x, fractionalWidth)
    val neg = bi < 0
    val neededWidth = if (neg) bi.bitLength + 1 else bi.bitLength
    require(neededWidth <= totalWidth, "Double -> BigInt width larger than total width allocated!")
    if (neg) (BigInt(1) << neededWidth) + bi 
    else bi
  }

  // Redundant from chisel-testers
  // Converts unsigned BigInt to signed BigInt (width = width of Chisel data type)
  def signConvert(bigInt: BigInt, width: Int): BigInt = {
    require(bigInt >= 0, "signConvert assumes bigInt is >= 0!")
    // Since the bigInt is always unsigned, bitLength always gets the max # of bits required to represent bigInt
    val w = bigInt.bitLength.max(width)
    // Negative if MSB is set or in this case, ex: 3 bit wide: negative if >= 4
    if (bigInt >= (BigInt(1) << (w - 1))) (bigInt - (BigInt(1) << w)) else bigInt
  }

  // Converts a positive 2's complement BigInt to a Double - used for FixedPoint
  def toDoubleFromUnsigned(i: BigInt, totalWidth: Int, fractionalWidth: Int): Double = {
    val signedBigInt = signConvert(i, totalWidth)
    FixedPoint.toDouble(signedBigInt, fractionalWidth)
  }

  // For DspReal represented as BigInt from Double
  def doubleToBigIntBits(double: Double): BigInt = {
    val ret = BigInt(java.lang.Double.doubleToLongBits(double))
    if (ret >= 0) ret
    else (BigInt(1) << DspReal.underlyingWidth) + rest
  }

  // For DspReal represented as BigInt back to Double
  def bigIntBitsToDouble(bigInt: BigInt): Double = {
    java.lang.Double.longBitsToDouble(bigInt.toLong)
  }

}