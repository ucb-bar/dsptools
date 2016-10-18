// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import dsptools.{DspContext, DspException}
import spire.algebra.Ring
import spire.implicits._

object DspComplex {
  def apply[T <: Data:Ring](real: T, imaginary: T): DspComplex[T] = {
    new DspComplex(real.cloneType, imaginary.cloneType)
  }
  def wire[T <: Data:Ring](real: T, imaginary: T): DspComplex[T] = {
    val result = Wire(new DspComplex(real.cloneType, imaginary.cloneType))
    result.real := real
    result.imaginary := imaginary
    result
  }
  def multiplyByJ[T <: Data:Ring](x: DspComplex[T]): DspComplex[T] =
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
  * @param context a context object describing SInt behavior
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

trait DspComplexImpl {
  implicit def DspComplexRingImpl[T<:Data:Ring] = new DspComplexRing[T]()
  implicit object DspComplexFixedPointRing extends DspComplexRing[FixedPoint]()(new FixedPointRing {})
  implicit object DspComplexDspRealRing extends DspComplexRing[DspReal]()(new DspRealRing{})
}
