// SPDX-License-Identifier: Apache-2.0

package dsptools

import chisel3._
import chisel3.experimental.FixedPoint
import dsptools.numbers.{DspComplex, DspReal}
import chisel3.internal.InstanceId
import chisel3.internal.firrtl.KnownBinaryPoint

// TODO: Get rid of
import chisel3.iotesters.TestersCompatibility

//scalastyle:off cyclomatic.complexity method.length
object DspTesterUtilities {
 
  // Converts signed Double's to their 2's complement BigInt equivalents (unsigned)
  // (totalWidth, fractionalWidth of some FixedPoint)
  def signedToBigIntUnsigned(x: Double, totalWidth: Int, fractionalWidth: Int): BigInt = {
    val bi = FixedPoint.toBigInt(x, fractionalWidth)
    val neg = bi < 0
    val neededWidth = bi.bitLength + 1
    require(neededWidth <= totalWidth, "Double -> BigInt width larger than total width allocated!")
    if (neg) {
      (BigInt(1) << totalWidth) + bi
    } else {
      bi
    }
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

  // For DspReal represented as BigInt from Double (unsigned)
  def doubleToBigIntBits(double: Double): BigInt = {
    val ret = BigInt(java.lang.Double.doubleToLongBits(double))
    if (ret >= 0) ret
    else (BigInt(1) << DspReal.underlyingWidth) + ret
  }

  // For DspReal represented as BigInt back to Double
  def bigIntBitsToDouble(bigInt: BigInt): Double = {
    java.lang.Double.longBitsToDouble(bigInt.toLong)
  }

  // Used to get signal name for printing to console
  private [dsptools] def getName(signal: InstanceId): String = {
    s"${signal.parentPathName}.${TestersCompatibility.validName(signal.instanceName)}"
  }

  // Note: DspReal underlying is UInt
  // Checks if a basic number is signed or unsigned
  def isSigned(e: Data): Boolean = {
    e match {
      case _: SInt | _: FixedPoint => true
      case _: DspReal | _: Bool | _: UInt => false
      // Clock isn't a number, but it's still valid IO (should be treated as a Bool)
      case _: Clock => false
      case _ => throw DspException("Not a basic number/clock type! " + e)
    }
  }

  // For printing to Verilog testbench (signed)
  private [dsptools] def signPrefix(e: Element): String = {
    def signed = isSigned(e)
    if (signed) " signed "
    else ""
  }

  // Determines if peek/poke data fits in bit width
  def validRangeTest(signal: Data, value: BigInt) {
    val len = value.bitLength
    val neededLen = if (isSigned(signal)) len + 1 else len
    require(signal.widthOption.nonEmpty, "Cannot check range of node with unknown width!")
    if (neededLen > signal.getWidth) 
      throw DspException(s"Value: $value is not in node ${getName(signal)} range")
    if (!isSigned(signal) && value < 0)
      throw DspException("Negative value can't be used with unsigned")
  }

  // Gets information on bitwidth, binarypoint for printing in console
  def bitInfo(signal: Data): String = signal.widthOption match {
    case Some(width) => {
      signal match {
        case f: FixedPoint => f.binaryPoint match {
          // Q integer . fractional bits
          case KnownBinaryPoint(bp) => s"Q${width - 1 - bp}.$bp"
          case _ => s"${width}-bit F"
        }
        case r: DspReal => "R"
        case u: UInt => s"${width}-bit U"
        case s: SInt => s"${width}-bit S"
        case c: DspComplex[_] => {
          val realInfo = bitInfo(c.real.asInstanceOf[Data])
          val imagInfo = bitInfo(c.imag.asInstanceOf[Data])
          s"[$realInfo, $imagInfo]"
        }
        case _ => throw DspException("Can't get bit info! Invalid type!")
      }
    }
    case None => ""
  }

  // Round value if data type is integer
  def roundData(data: Data, value: Double): Double = {
    data match {
      case _: SInt | _: UInt => value.round
      case _: DspReal | _: FixedPoint => value
      case _ => throw DspException("Invalid data type for rounding determination")
    }
  }

}
