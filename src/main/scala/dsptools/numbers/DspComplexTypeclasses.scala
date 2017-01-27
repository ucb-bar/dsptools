// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import dsptools.hasContext
import implicits._

/**
  * Defines basic math functions for DspComplex
 *
  */
class DspComplexRing[T <: Data:Ring] extends Ring[DspComplex[T]] with hasContext {
  def plus(f: DspComplex[T], g: DspComplex[T]): DspComplex[T] = {
    DspComplex.wire(f.real + g.real, f.imaginary + g.imaginary)
  }
  def times(f: DspComplex[T], g: DspComplex[T]): DspComplex[T] = {
    DspComplex.wire(f.real * g.real - f.imaginary * g.imaginary, f.real * g.imaginary + f.imaginary * g.real)
  }
  def one: DspComplex[T] = DspComplex.wire(implicitly[Ring[T]].one, implicitly[Ring[T]].zero)
  override def fromInt(x: Int): DspComplex[T] = DspComplex.wire(implicitly[Ring[T]].fromInt(x), implicitly[Ring[T]].zero)
  def zero: DspComplex[T] = DspComplex.wire(implicitly[Ring[T]].zero, implicitly[Ring[T]].zero)
  def negate(f: DspComplex[T]): DspComplex[T] = DspComplex.wire(-f.real, -f.imaginary)
  override def minus(f: DspComplex[T], g: DspComplex[T]): DspComplex[T] = {
    DspComplex.wire(f.real - g.real, f.imaginary - g.imaginary)
  }
}

class ConvertableToDspComplex[T <: Data : Ring : ConvertableTo] extends ConvertableTo[DspComplex[T]] with hasContext {
  def fromShort(n: Short): DspComplex[T] =
    DspComplex.wire(ConvertableTo[T].fromShort(n), ConvertableTo[T].fromInt(0))
  def fromBigInt(n: BigInt): DspComplex[T] =
    DspComplex.wire(ConvertableTo[T].fromBigInt(n), ConvertableTo[T].fromInt(0))
  def fromByte(n: Byte): DspComplex[T] =
    DspComplex.wire(ConvertableTo[T].fromByte(n), ConvertableTo[T].fromInt(0))
  def fromDouble(n: Double): DspComplex[T] =
    DspComplex.wire(ConvertableTo[T].fromDouble(n), ConvertableTo[T].fromInt(0))
  def fromType[B](n: B)(implicit c: ConvertableFrom[B]): DspComplex[T] =
    DspComplex.wire(ConvertableTo[T].fromDouble(c.toDouble(n)), ConvertableTo[T].fromInt(0))
  def fromInt(n: Int): DspComplex[T] =
    DspComplex.wire(ConvertableTo[T].fromInt(n), ConvertableTo[T].fromInt(0))
  def fromFloat(n: Float): DspComplex[T] =
    DspComplex.wire(ConvertableTo[T].fromFloat(n), ConvertableTo[T].fromInt(0))
  def fromBigDecimal(n: BigDecimal): DspComplex[T] =
    DspComplex.wire(ConvertableTo[T].fromBigDecimal(n), ConvertableTo[T].fromInt(0))
  def fromLong(n: Long): DspComplex[T] =
    DspComplex.wire(ConvertableTo[T].fromLong(n), ConvertableTo[T].fromInt(0))
  def fromAlgebraic(n: spire.math.Algebraic): DspComplex[T] = fromDouble(n.toDouble)
  def fromReal(n: spire.math.Real): DspComplex[T] = fromDouble(n.toDouble)
  def fromRational(n: spire.math.Rational): DspComplex[T] = fromDouble(n.toDouble)
}

trait DspComplexImpl {
  implicit def DspComplexRingImpl[T<:Data:Ring] = new DspComplexRing[T]()
  implicit def ConvertableToDspComplex[T<:Data:Ring:ConvertableTo] =
    new ConvertableToDspComplex[T]()
}
