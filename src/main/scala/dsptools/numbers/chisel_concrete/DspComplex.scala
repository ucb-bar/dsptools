// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3._
import chisel3.experimental.FixedPoint
import dsptools.DspException
import breeze.math.Complex
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor

object DspComplex {

  def apply[T <: Data:Ring](gen: T): DspComplex[T] = {
    if (gen.isLit) throw DspException("Cannot use Lit in single argument DspComplex.apply")
    apply(gen.cloneType, gen.cloneType)
  }

  // If real, imag are literals, the literals are carried through
  // In reality, real and imag should have the same type, so should be using single argument
  // apply if you aren't trying t create a Lit
  def apply[T <: Data:Ring](real: T, imag: T): DspComplex[T] = {
    val newReal = if (real.isLit) real.cloneType else real
    val newImag = if (imag.isLit) imag.cloneType else imag
    if(real.isLit && imag.isLit) {
      new DspComplex(newReal, newImag).Lit(_.real -> real, _.imag -> imag)
    } else {
      new DspComplex(newReal, newImag)
    }
  }

  // Needed for assigning to results of operations; should not use in user code for making wires
  // Assumes real, imag are not literals
  def wire[T <: Data:Ring](real: T, imag: T): DspComplex[T] = {
    val result = Wire(DspComplex(real.cloneType, imag.cloneType))
    result.real := real
    result.imag := imag
    result
  }

  // Constant j
  // TODO(Paul): this call to wire() should be removed when chisel has literal bundles
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

class DspComplex[T <: Data:Ring](val real: T, val imag: T) extends Bundle {
  
  // So old DSP code doesn't break
  def imaginary(dummy: Int = 0): T = imag

  // Multiply by j
  def mulj(dummy: Int = 0): DspComplex[T] = DspComplex.wire(-imag, real)
  // Divide by j
  def divj(dummy: Int = 0): DspComplex[T] = DspComplex.wire(imag, -real)
  // Complex conjugate
  def conj(dummy: Int = 0): DspComplex[T] = DspComplex.wire(real, -imag)
  // Absolute square (squared norm) = x^2 + y^2
  // Uses implicits
  def abssq(dummy: Int = 0): T = (real * real) + (imag * imag)

  def underlyingType(dummy: Int = 0): String = {
    real match {
      case _: FixedPoint => "fixed"
      case _: DspReal    => "real"
      case _: SInt       => "SInt"
      case _: UInt       => "UInt"
      case _ => throw DspException(s"DspComplex found unsupported underlying type: ${real.getClass.getName}")
    }
  }
}
