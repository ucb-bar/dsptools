// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import dsptools.{hasContext, DspContext, Grow, Saturate}

import scala.language.implicitConversions

//scalastyle:off magic.number

/**
  * Defines basic math functions for UInt
  */
trait UIntRing extends Any with Ring[UInt] with hasContext {
  def plus(f: UInt, g: UInt): UInt = {
    if(context.overflowType == Grow) {
      f +& g
    } else if (context.overflowType == Saturate) {
      println("Saturating add is broken right now!")
      val grow = f +& g
      val nogrow = f +% g
      val max = 3.U
      val min = (-4).U

      Mux(grow === nogrow,
        nogrow,
        Mux(grow > 0.U, max, min)
      )
    } else {
      f +% g
    }
  }
  def times(f: UInt, g: UInt): UInt = {
    f * g
  }
  def one: UInt = BigInt(1).U
  def zero: UInt = BigInt(0).U
  def negate(f: UInt): UInt = -f
  override def minus(f: UInt, g: UInt): UInt = {
    if(context.overflowType == Grow) {
      f -& g
    } else if (context.overflowType == Saturate) {
      println("Saturating subtract is broken right now!")
      val grow = f -& g
      val nogrow = f -% g
      val max = 3.U
      val min = 0.U

      Mux(grow === nogrow,
        nogrow,
        Mux(grow > 0.U, max, min)
      )
    } else {
      f -% g
    }
  }
}

trait UIntImpl {
  implicit object UIntIntegerImpl extends UIntInteger
  implicit def fromInt(x: Double): UInt = x.toInt.U
}

trait UIntOrder extends Any with Order[UInt] with hasContext {
  override def compare(x: UInt, y: UInt): ComparisonBundle = {
    ComparisonHelper(x === y, x < y)
  }
}

trait UIntSigned extends Any with Signed[UInt] with hasContext {
  def signum(a: UInt): ComparisonBundle = {
    ComparisonHelper(a === 0.U, a < 0.U)
  }

  /** An idempotent function that ensures an object has a non-negative sign. */
  def abs(a: UInt): UInt = a // UInts are unsigned!
}
trait UIntIsReal extends Any with IsIntegral[UInt] with UIntOrder with UIntSigned with hasContext {
  def toDouble(a: UInt): DspReal = ???
}

trait ConvertableToUInt extends ConvertableTo[UInt] with hasContext {
  def fromShort(n: Short): UInt = n.toInt.U
  //def fromAlgebraic(n: Algebraic): UInt = n.toBigInt.U
  def fromBigInt(n: BigInt): UInt = n.U
  def fromByte(n: Byte): UInt = n.toInt.U
  def fromDouble(n: Double): UInt = n.toInt.U
//  def fromReal(n: Real): UInt = n.toInt.U
  //def fromRational(n: Rational): UInt = n.toInt.U
  def fromType[B](n: B)(implicit c: ConvertableFrom[B]): UInt = c.toBigInt(n).U
  def fromInt(n: Int): UInt = n.U
  def fromFloat(n: Float): UInt = n.toInt.U
  def fromBigDecimal(n: BigDecimal): UInt = n.toBigInt.U
  def fromLong(n: Long): UInt = n.U
}

trait UIntInteger extends UIntRing with ConvertableToUInt with UIntIsReal with Integer[UInt] with hasContext {
  override def fromInt(n: Int): UInt = super[UIntRing].fromInt(n)
  def mod(a: UInt, b: UInt): UInt = a % b
}
