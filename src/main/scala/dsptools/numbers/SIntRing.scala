// See LICENSE for license details.

package dsptools.numbers

import chisel3.{Mux, SInt, UInt, Wire}
import dsptools.{DspContext, Grow, Saturate}
import spire.algebra.Ring
import spire.math.{Algebraic, ConvertableFrom, ConvertableTo, Rational}

/**
  * Defines basic math functions for SInt
  * @param context a context object describing SInt behavior
  */
trait SIntRing extends Any with Ring[SInt] with hasContext {
  def plus(f: SInt, g: SInt): SInt = {
    if(context.overflowType == Grow) {
      f +& g
    } else if (context.overflowType == Saturate) {
      println("Saturating add is broken right now!")
      val grow = f +& g
      val nogrow = f +% g
      val max = SInt(3)
      val min = SInt(-4)

      Mux(grow === nogrow,
        nogrow,
        Mux(grow > SInt(0), max, min )
      )
    } else {
      f +% g
    }
  }
  def times(f: SInt, g: SInt): SInt = {
    f * g
  }
  def one: SInt = SInt(value = BigInt(1))
  def zero: SInt = SInt(value = BigInt(0))
  def negate(f: SInt): SInt = zero - f
}

trait SIntImpl {
  implicit object SIntIntegerImpl extends SIntInteger
  implicit def fromInt(x: Double): SInt = SInt(x.toInt)
}

trait SIntOrder extends Any with Order[SInt] with hasContext {
  override def compare(x: SInt, y: SInt): ComparisonBundle = {
    ComparisonHelper(x === y, x < y)
  }
}

trait SIntSigned extends Any with Signed[SInt] with hasContext {
  def signum(a: SInt): ComparisonBundle = {
    ComparisonHelper(a === SInt(0), a < SInt(0))
  }

  /** An idempotent function that ensures an object has a non-negative sign. */
  def abs(a: SInt): SInt = a.abs().zext()
}
trait SIntIsReal extends Any with IsIntegral[SInt] with SIntOrder with SIntSigned with hasContext {
  def toDouble(a: SInt): DspReal = ???
}

trait ConvertableToSInt extends ConvertableTo[SInt] with hasContext {
  def fromShort(n: Short): SInt = SInt(n.toInt)
  def fromAlgebraic(n: Algebraic): SInt = SInt(n.toBigInt)
  def fromBigInt(n: BigInt): SInt = SInt(n)
  def fromByte(n: Byte): SInt = SInt(n.toInt)
  def fromDouble(n: Double): SInt = SInt(n.toInt)
//  def fromReal(n: Real): SInt = SInt(n.toInt)
  def fromRational(n: Rational): SInt = SInt(n.toInt)
  def fromType[B](n: B)(implicit c: ConvertableFrom[B]): SInt = SInt(c.toBigInt(n))
  def fromInt(n: Int): SInt = SInt(n)
  def fromFloat(n: Float): SInt = SInt(n.toInt)
  def fromBigDecimal(n: BigDecimal): SInt = SInt(n.toBigInt)
  def fromLong(n: Long): SInt = SInt(n)
}

trait SIntInteger extends SIntRing with ConvertableToSInt with SIntIsReal with Integer[SInt] with hasContext {
  override def fromInt(n: Int): SInt = super[SIntRing].fromInt(n)
  def mod(a: SInt, b: SInt): SInt = a % b
}
