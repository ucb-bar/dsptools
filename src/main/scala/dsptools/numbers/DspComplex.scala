// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import chisel3.experimental.FixedPoint
import dsptools.DspException
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
