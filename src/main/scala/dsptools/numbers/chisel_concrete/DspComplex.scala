// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import chisel3.experimental.FixedPoint
import dsptools.DspException
import implicits._
import breeze.math.Complex
import scala.collection.immutable.ListMap

object DspComplex {

  def apply[T <: Data:Ring](gen: T): DspComplex[T] = {
    if (gen.isLit()) throw DspException("Cannot use Lit in single argument DspComplex.apply")
    apply(gen, gen)
  }

  // If real, imag are literals, the literals are carried through
  // In reality, real and imag should have the same type, so should be using single argument
  // apply if you aren't trying t create a Lit
  def apply[T <: Data:Ring](real: T, imag: T): DspComplex[T] = {
    if (real.isLit())  require(imag.isLit(),  "real was a literal, so imag should also be a literal")
    if (!real.isLit()) require(!imag.isLit(), "real was not a literal, so imag should also not be a literal")
    val newReal = if (real.isLit()) real else real.cloneType
    val newImag = if (imag.isLit()) imag else imag.cloneType
    new DspComplex(newReal, newImag)
  }

  // Needed for assigning to results of operations; should not use in user code for making wires
  // Assumes real, imag are not literals
  private [dsptools] def wire[T <: Data:Ring](real: T, imag: T): DspComplex[T] = {
    val result = Wire(DspComplex(real.cloneType, imag.cloneType))
    result.real := real
    result.imag := imag
    result
  }

  // Constant j
  def j[T <: Data:Ring] : DspComplex[T] = DspComplex(Ring[T].zero, Ring[T].one)

  // Creates a DspComplex literal of type DspComplex[T] from a Breeze Complex
  // Note: when T is FixedPoint, the # of fractional bits is determined via DspContext
  def apply[T <: Data:Ring:ConvertableTo](c: Complex): DspComplex[T] = {
    DspComplex(ConvertableTo[T].fromDouble(c.real), ConvertableTo[T].fromDouble(c.imag))
  }
  // Creates a DspComplex literal where real and imaginary parts have type T (and binary point 
  // determined by binaryPoint of t)
  def proto[T <: Data:Ring:ConvertableTo](c: Complex, t: T): DspComplex[T] = {
    DspComplex(ConvertableTo[T].fromDouble(c.real, t), ConvertableTo[T].fromDouble(c.imag, t))
  }
  // Creates a DspComplex literal where real and imaginary parts have type T (width/binary point 
  // determined by width/binaryPoint of t)
  def protoWithFixedWidth[T <: Data:Ring:ConvertableTo](c: Complex, t: T): DspComplex[T] = {
    DspComplex(ConvertableTo[T].fromDoubleWithFixedWidth(c.real, t), 
      ConvertableTo[T].fromDoubleWithFixedWidth(c.imag, t))
  }

}

class DspComplex[T <: Data:Ring](val real: T, val imag: T) extends Record {
  val elements = ListMap( "real" -> real, "imag" -> imag )
  
  def imaginary: T = imag

  // Multiply by j
  def mulj(): DspComplex[T] = DspComplex.wire(-imag, real)
  // Divide by j
  def divj(): DspComplex[T] = DspComplex.wire(imag, -real)
  // Complex conjugate
  def conj(): DspComplex[T] = DspComplex.wire(real, -imag)
  // Absolute square (squared norm) = x^2 + y^2
  // Uses implicits
  def abssq(): T = (real * real) + (imag * imag)

  override def cloneType: this.type = {
    new DspComplex(real.cloneType, imag.cloneType).asInstanceOf[this.type]
  }

  def underlyingType: String = {
    real match {
      case f: FixedPoint => "fixed"
      case r: DspReal    => "real"
      case s: SInt       => "SInt"
      case u: UInt       => "UInt"
      case _ => throw DspException(s"DspComplex found unsupported underlying type: ${real.getClass.getName}")
    }
  }
}
