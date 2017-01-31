// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import dsptools.hasContext

trait DspRealRing extends Any with Ring[DspReal] with hasContext {
  def plus(f: DspReal, g: DspReal): DspReal = {
    f + g
  }
  def times(f: DspReal, g: DspReal): DspReal = {
    f * g
  }
//  def one: DspReal = DspReal.wire(DspReal(1.0))
//  def zero: DspReal = DspReal.wire(DspReal(0.0))
  def one: DspReal = DspReal(1.0)
  def zero: DspReal = DspReal(0.0)
  def negate(f: DspReal): DspReal = zero - f
  override def minus(f: DspReal, g: DspReal): DspReal = {
    f - g
  }
}

trait DspRealImpl {
  implicit object DspRealRealImpl extends DspRealReal
}

trait DspRealOrder extends Any with Order[DspReal] with hasContext {
  override def compare(x: DspReal, y: DspReal): ComparisonBundle = {
    ComparisonHelper(x === y, x < y)
  }
}

trait DspRealSigned extends Any with Signed[DspReal] with hasContext {
  def signum(a: DspReal): ComparisonBundle = {
    ComparisonHelper(a === DspReal(0), a < DspReal(0))
  }

  /** An idempotent function that ensures an object has a non-negative sign. */
  def abs(a: DspReal): DspReal = Mux(a > DspReal(0), a, DspReal(0)-a)
}
trait DspRealIsReal extends Any with IsReal[DspReal] with DspRealOrder with DspRealSigned with hasContext {
  def toDouble(a: DspReal): DspReal = a

  def ceil(a: DspReal): DspReal = a.ceil()
  def floor(a: DspReal): DspReal = a.floor()
  def isWhole(a: DspReal): Bool = a === round(a)
  def round(a: DspReal): DspReal = 
    Mux(a >= DspReal(0.0),
      floor(a + DspReal(0.5)),
      ceil (a + DspReal(0.5))
      )

  def truncate(a: DspReal): DspReal = ???
}

trait ConvertableToDspReal extends ConvertableTo[DspReal] with hasContext {
  


  override def fromDouble(d: Double, a: DspReal): DspReal = DspReal(d)
  override def fromDoubleWithFixedWidth(d: Double, a: DspReal): DspReal = DspReal(d)


  def fromShort(n: Short): DspReal = DspReal(n.toInt)
  //def fromAlgebraic(n: Algebraic): DspReal = DspReal(n.toInt)
  def fromBigInt(n: BigInt): DspReal = DspReal(n.toInt)
  def fromByte(n: Byte): DspReal = DspReal(n.toInt)
  def fromDouble(n: Double): DspReal = DspReal(n)
  //def fromReal(n: Real): DspReal = DspReal(n.toDouble)
  //def fromRational(n: Rational): DspReal = DspReal(n.toDouble)
  def fromType[B](n: B)(implicit c: ConvertableFrom[B]): DspReal = DspReal(c.toDouble(n))
  def fromInt(n: Int): DspReal = DspReal(n)
  def fromFloat(n: Float): DspReal = DspReal(n.toDouble)
  def fromBigDecimal(n: BigDecimal): DspReal = DspReal(n.toDouble)
  def fromLong(n: Long): DspReal = DspReal(n)
}

trait DspRealReal extends DspRealRing with ConvertableToDspReal with DspRealIsReal with Real[DspReal] with hasContext {
  override def fromInt(n: Int): DspReal = super[DspRealRing].fromInt(n)
}
