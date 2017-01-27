// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import dsptools.{hasContext, DspContext, Grow, Saturate}

import scala.language.implicitConversions

//scalastyle:off magic.number

/**
  * Defines basic math functions for SInt
  */
trait SIntRing extends Any with Ring[SInt] with hasContext {
  def plus(f: SInt, g: SInt): SInt = {
    if(context.overflowType == Grow) {
      f +& g
    } else if (context.overflowType == Saturate) {
      println("Saturating add is broken right now!")
      val grow = f +& g
      val nogrow = f +% g
      val max = 3.S
      val min = (-4).S

      Mux(grow === nogrow,
        nogrow,
        Mux(grow > 0.S, max, min)
      )
    } else {
      f +% g
    }
  }
  def times(f: SInt, g: SInt): SInt = {
    f * g
  }
  def one: SInt = BigInt(1).S
  def zero: SInt = BigInt(0).S
  def negate(f: SInt): SInt = -f
  override def minus(f: SInt, g: SInt): SInt = {
    if(context.overflowType == Grow) {
      f -& g
    } else if (context.overflowType == Saturate) {
      println("Saturating add is broken right now!")
      val grow = f -& g
      val nogrow = f -% g
      val max = 3.S
      val min = (-4).S

      Mux(grow === nogrow,
        nogrow,
        Mux(grow > 0.S, max, min)
      )
    } else {
      f -% g
    }
  }
}

trait SIntImpl {
  implicit object SIntIntegerImpl extends SIntInteger
  implicit def fromInt(x: Double): SInt = x.toInt.S
}

trait SIntOrder extends Any with Order[SInt] with hasContext {
  override def compare(x: SInt, y: SInt): ComparisonBundle = {
    ComparisonHelper(x === y, x < y)
  }
}

trait SIntSigned extends Any with Signed[SInt] with hasContext {
  def signum(a: SInt): ComparisonBundle = {
    ComparisonHelper(a === 0.S, a < 0.S)
  }

  /** An idempotent function that ensures an object has a non-negative sign. */
  def abs(a: SInt): SInt = a.abs().zext()
}
trait SIntIsReal extends Any with IsIntegral[SInt] with SIntOrder with SIntSigned with hasContext {
  def toDouble(a: SInt): DspReal = ???
}

trait ConvertableToSInt extends ConvertableTo[SInt] with hasContext {
  def fromShort(n: Short): SInt = n.toInt.S
  //def fromAlgebraic(n: Algebraic): SInt = n.toBigInt.S
  def fromBigInt(n: BigInt): SInt = n.S
  def fromByte(n: Byte): SInt = n.toInt.S
  def fromDouble(n: Double): SInt = n.toInt.S
//  def fromReal(n: Real): SInt = n.toInt.S
  //def fromRational(n: Rational): SInt = n.toInt.S
  def fromType[B](n: B)(implicit c: ConvertableFrom[B]): SInt = c.toBigInt(n).S
  def fromInt(n: Int): SInt = n.S
  def fromFloat(n: Float): SInt = n.toInt.S
  def fromBigDecimal(n: BigDecimal): SInt = n.toBigInt.S
  def fromLong(n: Long): SInt = n.S
}

trait SIntInteger extends SIntRing with ConvertableToSInt with SIntIsReal with Integer[SInt] with hasContext {
  override def fromInt(n: Int): SInt = super[SIntRing].fromInt(n)
  def mod(a: SInt, b: SInt): SInt = a % b
}
