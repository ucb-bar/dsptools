// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import dsptools.hasContext
import implicits._

/* TODO: Eq, convertableTo, convertableFrom, div2, multiplyby2, use4muls*/

class DspComplexRing[T <: Data:Ring] extends Ring[DspComplex[T]] with hasContext {



  def plus(f: DspComplex[T], g: DspComplex[T]): DspComplex[T] = {
    DspComplex.wire(f.real + g.real, f.imag + g.imag)
  }
  def times(f: DspComplex[T], g: DspComplex[T]): DspComplex[T] = {
    DspComplex.wire(f.real * g.real - f.imag * g.imag, f.real * g.imag + f.imag * g.real)
  }
  def one: DspComplex[T] = DspComplex.wire(implicitly[Ring[T]].one, implicitly[Ring[T]].zero)
  override def fromInt(x: Int): DspComplex[T] = DspComplex.wire(implicitly[Ring[T]].fromInt(x), implicitly[Ring[T]].zero)
  def zero: DspComplex[T] = DspComplex.wire(implicitly[Ring[T]].zero, implicitly[Ring[T]].zero)
  def negate(f: DspComplex[T]): DspComplex[T] = DspComplex.wire(-f.real, -f.imag)
  override def minus(f: DspComplex[T], g: DspComplex[T]): DspComplex[T] = {
    DspComplex.wire(f.real - g.real, f.imag - g.imag)
  }
}



trait DspComplexImpl {
  implicit def DspComplexRingImpl[T<:Data:Ring] = new DspComplexRing[T]()
}
