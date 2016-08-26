// See LICENSE for license details.

package dsptools.numbers

import chisel3.core.{FixedPoint, Wire}
import chisel3.{SInt, Data, Bundle}
import dsptools.{DspException, Grow, DspContext}
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
class DspComplexRing[T <: Data:Ring](implicit context: DspContext) extends Ring[DspComplex[T]] {
  def plus(f: DspComplex[T], g: DspComplex[T]): DspComplex[T] = {
    DspComplex.wire(f.real + g.real, f.imaginary + g.imaginary)
  }
  def times(f: DspComplex[T], g: DspComplex[T]): DspComplex[T] = {
    DspComplex.wire(f.real * g.real - f.imaginary * g.imaginary, f.real * g.imaginary + f.imaginary * g.real)
  }
  def one: DspComplex[T] = DspComplex.wire(implicitly[Ring[T]].one, implicitly[Ring[T]].zero)
  def zero: DspComplex[T] = DspComplex.wire(implicitly[Ring[T]].zero, implicitly[Ring[T]].zero)
  def negate(f: DspComplex[T]): DspComplex[T] = DspComplex.wire(-f.real, -f.imaginary)
}

case class DspFixedPointComplex(override val real: FixedPoint, override val imaginary: FixedPoint)(implicit ev: FixedPointRing)
  extends DspComplex[FixedPoint](real, imaginary)

