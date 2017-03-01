// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import dsptools.hasContext
import implicits._
import chisel3.util.ShiftRegister
import dsptools.DspException

class DspComplexRing[T <: Data:Ring] extends Ring[DspComplex[T]] with hasContext {
  def plus(f: DspComplex[T], g: DspComplex[T]): DspComplex[T] = {
    DspComplex.wire(f.real + g.real, f.imag + g.imag)
  }
  def times(f: DspComplex[T], g: DspComplex[T]): DspComplex[T] = {
    if (context.complexUse4Muls)
      DspComplex.wire((f.real * g.real) - (f.imag * g.imag), (f.real * g.imag) + (f.imag * g.real))
    else {
      val fRealDly = ShiftRegister(f.real, context.numAddPipes)
      val gRealDly = ShiftRegister(g.real, context.numAddPipes)
      val gImagDly = ShiftRegister(g.imag, context.numAddPipes)
      val c_p_d = g.real + g.imag
      val a_p_b = f.real + f.imag
      val b_m_a = f.imag - f.real
      val ac_p_ad = fRealDly * c_p_d
      val ad_p_bd = a_p_b * gImagDly
      val bc_m_ac = b_m_a * gRealDly
      DspComplex.wire(ac_p_ad - ad_p_bd, ac_p_ad + bc_m_ac)
    }
  }
  def one: DspComplex[T] = DspComplex(Ring[T].one, Ring[T].zero)
  // Only assigns real part as x
  override def fromInt(x: Int): DspComplex[T] = DspComplex(Ring[T].fromInt(x), Ring[T].zero)
  def zero: DspComplex[T] = DspComplex(Ring[T].zero, Ring[T].zero)
  def negate(f: DspComplex[T]): DspComplex[T] = DspComplex.wire(-f.real, -f.imag)
  override def minus(f: DspComplex[T], g: DspComplex[T]): DspComplex[T] = {
    DspComplex.wire(f.real - g.real, f.imag - g.imag)
  }
}

class DspComplexEq[T <: Data:Eq] extends Eq[DspComplex[T]] with hasContext {
  override def eqv(x: DspComplex[T], y: DspComplex[T]): Bool = {
    (x.real === y.real) && (x.imag === y.imag)
  }
  override def neqv(x: DspComplex[T], y: DspComplex[T]): Bool = {
    (x.real =/= y.real) || (x.imag =/= y.imag)
  }
}

class DspComplexBinaryRepresentation[T <: Data:Ring:BinaryRepresentation] extends 
    BinaryRepresentation[DspComplex[T]] with hasContext {
  override def shl(a: DspComplex[T], n: Int): DspComplex[T] = throw DspException("Can't shl on complex")
  override def shl(a: DspComplex[T], n: UInt): DspComplex[T] = throw DspException("Can't shl on complex")
  override def shr(a: DspComplex[T], n: Int): DspComplex[T] = throw DspException("Can't shr on complex")
  override def shr(a: DspComplex[T], n: UInt): DspComplex[T] = throw DspException("Can't shr on complex")
  override def div2(a: DspComplex[T], n: Int): DspComplex[T] = DspComplex.wire(a.real.div2(n), a.imag.div2(n))
  override def mul2(a: DspComplex[T], n: Int): DspComplex[T] = DspComplex.wire(a.real.mul2(n), a.imag.mul2(n))
  def signBit(a: DspComplex[T]): Bool = throw DspException("Can't get sign bit on complex")
  def trimBinary(a: DspComplex[T], n: Option[Int]): DspComplex[T] = 
    DspComplex.wire(BinaryRepresentation[T].trimBinary(a.real, n), BinaryRepresentation[T].trimBinary(a.imag, n))
}

trait DspComplexImpl {
  implicit def DspComplexRingImpl[T<: Data:Ring] = new DspComplexRing[T]()
  implicit def DspComplexEq[T <: Data:Eq] = new DspComplexEq[T]()
  implicit def DspComplexBinaryRepresentation[T <: Data:Ring:BinaryRepresentation] = 
    new DspComplexBinaryRepresentation[T]()
}
