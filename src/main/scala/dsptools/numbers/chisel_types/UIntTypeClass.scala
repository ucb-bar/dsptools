// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3._
import chisel3.util.{Cat, ShiftRegister}
import dsptools.{DspContext, DspException, Grow, Saturate, Wrap, hasContext}
import chisel3.experimental.FixedPoint

import scala.language.implicitConversions

/**
  * Defines basic math functions for UInt
  */
trait UIntRing extends Any with Ring[UInt] with hasContext {
  def zero: UInt = 0.U
  def one: UInt = 1.U
  def plus(f: UInt, g: UInt): UInt = f + g
  def plusContext(f: UInt, g: UInt): UInt = {
    // TODO: Saturating mux should be outside of ShiftRegister
    val sum = context.overflowType match {
      case Grow => f +& g
      case Wrap => f +% g
      case _ => throw DspException("Saturating add hasn't been implemented")
    }
    ShiftRegister(sum, context.numAddPipes)
  }
  override def minus(f: UInt, g: UInt): UInt = f - g
  def minusContext(f: UInt, g: UInt): UInt = {
    val diff = context.overflowType match {
      case Grow => throw DspException("OverflowType Grow is not supported for UInt subtraction")
      case Wrap => f -% g
      case _ => throw DspException("Saturating subtractor hasn't been implemented")
    }
    ShiftRegister(diff.asUInt, context.numAddPipes)
  }
  def negate(f: UInt): UInt = -f
  def negateContext(f: UInt): UInt = throw DspException("Can't negate UInt and get UInt")
  def times(f: UInt, g: UInt): UInt = f * g
  def timesContext(f: UInt, g: UInt): UInt = {
    // TODO: Overflow via ranging in FIRRTL?
    ShiftRegister(f * g, context.numMulPipes)
  }  
}

trait UIntOrder extends Any with Order[UInt] with hasContext {
  override def compare(x: UInt, y: UInt): ComparisonBundle = {
    ComparisonHelper(x === y, x < y)
  }
  override def eqv(x: UInt, y: UInt): Bool = x === y
  override def neqv(x: UInt, y:UInt): Bool = x =/= y
  override def lt(x: UInt, y: UInt): Bool = x < y
  override def lteqv(x: UInt, y: UInt): Bool = x <= y
  override def gt(x: UInt, y: UInt): Bool = x > y
  override def gteqv(x: UInt, y: UInt): Bool = x >= y
  // min, max depends on lt, gt
}

trait UIntSigned extends Any with Signed[UInt] with hasContext {
  def signum(a: UInt): ComparisonBundle = {
    ComparisonHelper(a === 0.U, a < 0.U)
  }
  def abs(a: UInt): UInt = a // UInts are unsigned!
  def context_abs(a: UInt): UInt = a // UInts are unsigned!
  override def isSignZero(a: UInt): Bool = a === 0.U
  override def isSignPositive(a: UInt): Bool = !isSignZero(a)
  override def isSignNegative(a: UInt): Bool = false.B
  // isSignNonZero, isSignNonPositive, isSignNonNegative derived from above (!)
}

trait UIntIsReal extends Any with IsIntegral[UInt] with UIntOrder with UIntSigned with hasContext {
  // In IsIntegral: ceil, floor, round, truncate (from IsReal) already defined as itself; 
  // isWhole always true
  
  // Unsure what happens if you have a zero-width wire
  def isOdd(a: UInt): Bool = a(0)
  // isEven derived from isOdd
  // Note: whatever Chisel does
  // Generally better to use your own mod if you know input bounds
  def mod(a: UInt, b: UInt): UInt = a % b
}

trait ConvertableToUInt extends ConvertableTo[UInt] with hasContext {
  // Note: Double converted to Int via round first!
  def fromShort(n: Short): UInt = fromInt(n.toInt)
  def fromByte(n: Byte): UInt = fromInt(n.toInt)
  def fromInt(n: Int): UInt = fromBigInt(BigInt(n))
  def fromFloat(n: Float): UInt = fromDouble(n.toDouble)
  def fromBigDecimal(n: BigDecimal): UInt = fromDouble(n.doubleValue)
  def fromLong(n: Long): UInt = fromBigInt(BigInt(n))
  def fromType[B](n: B)(implicit c: ConvertableFrom[B]): UInt = fromBigInt(c.toBigInt(n))
  def fromBigInt(n: BigInt): UInt = {
    require(n >= 0, "Literal to UInt needs to be >= 0")
    n.U
  }
  def fromDouble(n: Double): UInt = {
    require(n >= 0, "Double literal to UInt needs to be >= 0")
    n.round.toInt.U  
  }
  // Second argument needed for fixed pt binary point (unused here)
  override def fromDouble(d: Double, a: UInt): UInt = fromDouble(d)
  override def fromDoubleWithFixedWidth(d: Double, a: UInt): UInt = {
    require(a.widthKnown, "UInt width not known!")
    require(d >= 0, "Double literal to UInt needs to be >= 0")
    val intVal = d.round.toInt  
    val intBits = BigInt(intVal).bitLength
    require(intBits <= a.getWidth, "Lit can't fit in prototype UInt bitwidth")
    intVal.asUInt(a.getWidth.W)
  }
}

trait ConvertableFromUInt extends ChiselConvertableFrom[UInt] with hasContext {
  // Bit grow by 1, but always positive to maintain correctness
  def intPart(a: UInt): SInt = Cat(false.B, a).asSInt

  // Converts to FixedPoint with 0 fractional bits (second arg only used for DspReal)
  override def asFixed(a: UInt): FixedPoint = intPart(a).asFixedPoint(0.BP)
  def asFixed(a: UInt, proto: FixedPoint): FixedPoint = asFixed(a)
  // Converts to (signed) DspReal
  def asReal(a: UInt): DspReal = DspReal(intPart(a))
}

trait BinaryRepresentationUInt extends BinaryRepresentation[UInt] with hasContext {
  def shl(a: UInt, n: Int): UInt = a << n
  def shl(a: UInt, n: UInt): UInt = a << n
  def shr(a: UInt, n: Int): UInt = a >> n
  def shr(a: UInt, n: UInt): UInt = a >> n
  def clip(a: UInt, n: UInt): UInt = ???
  // Ignores negative trims (n not used for anything except Fixed)
  override def trimBinary(a: UInt, n: Int): UInt = a
  def trimBinary(a: UInt, n: Option[Int]): UInt = a
  // signBit relies on Signed
  // mul2, div2 consistent with shl, shr
 }

trait UIntInteger extends UIntRing with UIntIsReal with ConvertableToUInt with 
    ConvertableFromUInt with BinaryRepresentationUInt with IntegerBits[UInt] with hasContext {
  def signBit(a: UInt): Bool = isSignNegative(a)
  // fromUInt also included in Ring
  override def fromInt(n: Int): UInt = super[ConvertableToUInt].fromInt(n)
  override def fromBigInt(n: BigInt): UInt = super[ConvertableToUInt].fromBigInt(n)
}

trait UIntImpl {
  implicit object UIntIntegerImpl extends UIntInteger
}
