// See LICENSE for license details.

package dsptools.numbers

import chisel3.core.Wire
import chisel3.{Data, Bundle}
import dsptools.{Grow, DspContext}
import spire.algebra.Ring
import spire.implicits._

//case class DspComplex[T <: Data:Ring](real: T, imaginary: T) extends Bundle {
//  override def cloneType: this.type = {
//    DspComplex(real.cloneType, imaginary.cloneType).asInstanceOf[this.type]
//  }
//}

object DspComplex {
  def apply[T <: Data:Ring](real: T, imaginary: T): DspComplex[T] = {
    new DspComplex(real.cloneType, imaginary.cloneType)
  }
  def op[T <: Data:Ring](real: T, imaginary: T): DspComplex[T] = {
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
}
/**
  * Defines basic math functions for SInt
 *
  * @param context a context object describing SInt behavior
  */
class DspComplexRing[T <: Data:Ring](implicit context: DspContext) extends Ring[DspComplex[T]] {
  def plus(f: DspComplex[T], g: DspComplex[T]): DspComplex[T] = {
////    val realSum = Wire(init = f.real + g.real)
////    val imaginarySum = Wire(init = f.imaginary + g.imaginary)
//    val realSum = f.real + g.real
//    val imaginarySum = f.imaginary + g.imaginary
////    val realSum = Wire(f.real + g.real)
////    val imaginarySum = Wire(f.imaginary + g.imaginary)
//    val result = Wire(DspComplex(realSum.cloneType, imaginarySum.cloneType))
//    result.real := realSum
//    result.imaginary := imaginarySum
//    result
    DspComplex.op(f.real + g.real, f.imaginary + g.imaginary)
  }
  def times(f: DspComplex[T], g: DspComplex[T]): DspComplex[T] = {
    DspComplex(f.real * g.real - f.imaginary * g.imaginary,
      f.real * g.imaginary + f.imaginary * g.real)
  }
  def one: DspComplex[T] = DspComplex(implicitly[Ring[T]].one, implicitly[Ring[T]].zero)
  def zero: DspComplex[T] = DspComplex(implicitly[Ring[T]].zero, implicitly[Ring[T]].zero)
  def negate(f: DspComplex[T]): DspComplex[T] = DspComplex(-f.real, -f.imaginary)
}

class OneArgBundle[T <: Data:Ring](gen: T) extends Bundle {
  val real = gen.cloneType
  val imaginary = gen.cloneType
  override def cloneType: this.type = new OneArgBundle(gen).asInstanceOf[this.type]
}

class OneArgBundleRing[T <: Data:Ring](implicit context: DspContext) extends Ring[OneArgBundle[T]] {
  def plus(f: OneArgBundle[T], g: OneArgBundle[T]): OneArgBundle[T] = {
    val result = Wire(new OneArgBundle(f.real))
    result.real := f.real + g.real
    result.imaginary := f.imaginary + g.imaginary
    result
  }
  def times(f: OneArgBundle[T], g: OneArgBundle[T]): OneArgBundle[T] = {
    val result = new OneArgBundle(f.real)
    result.real := f.real * g.real - f.imaginary * g.imaginary
    result.imaginary := f.real * g.imaginary + f.imaginary * g.real
    result
  }
  def one: OneArgBundle[T] = {
    val result = new OneArgBundle(implicitly[Ring[T]].one)
    result.real := implicitly[Ring[T]].one
    result.imaginary := implicitly[Ring[T]].zero
    result
  }
  def zero: OneArgBundle[T] = {
    val result = new OneArgBundle(implicitly[Ring[T]].zero)
    result.real := implicitly[Ring[T]].zero
    result.imaginary := implicitly[Ring[T]].zero
    result
  }
  def negate(f: OneArgBundle[T]): OneArgBundle[T] = {
    val result = new OneArgBundle(f.real)
    result.real := -f.real
    result.imaginary := -f.imaginary
    result
  }

}