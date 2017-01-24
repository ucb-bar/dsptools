// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import chisel3.experimental.FixedPoint
import dsptools.{DspException, hasContext}
import implicits._

object DspComplex {

  def apply[T <: Data:Ring](gen: T): DspComplex[T] = {
    new DspComplex(gen.cloneType, gen.cloneType)
  }
  def apply[T <: Data:Ring](real: T, imaginary: T): DspComplex[T] = {
    if (real.isLit() && imaginary.isLit())
      new DspComplex(real, imaginary)
    else 
      new DspComplex(real.cloneType, imaginary.cloneType)
  }
  def wire[T <: Data:Ring](real: T, imaginary: T): DspComplex[T] = {
    val result = Wire(new DspComplex(real.cloneType, imaginary.cloneType))
    result.real := real
    result.imaginary := imaginary
    result
  }
  def j[T <: Data:Ring] : DspComplex[T] =
    wire(implicitly[Ring[T]].zero, implicitly[Ring[T]].one)

  def multiplyByJ[T <: Data:Ring](x: DspComplex[T]): DspComplex[T] =
    wire(-x.imaginary, x.real)

  def divideByJ[T <: Data:Ring](x: DspComplex[T]): DspComplex[T] =
    wire(x.imaginary, -x.real)
}
class DspComplex[T <: Data:Ring](val real: T, val imaginary: T) extends Bundle {
  override def cloneType: this.type = {
    new DspComplex(real.cloneType, imaginary.cloneType).asInstanceOf[this.type]
  }
  def underlyingType(dummy: Int = 0): String = {
    real match {
      case f: FixedPoint => "fixed"
      case r: DspReal    => "real"
      case s: SInt       => "SInt"
      case _ => throw DspException(s"DspComplex foud unsupported underlying type: ${real.getClass.getName}")
    }
  }
  elements
}
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
