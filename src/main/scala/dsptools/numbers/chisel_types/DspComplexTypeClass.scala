// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3._
import chisel3.experimental.FixedPoint
import dsptools.hasContext
import implicits._
import chisel3.util.ShiftRegister
import dsptools.DspException

abstract class DspComplexRing[T <: Data:Ring] extends Ring[DspComplex[T]] with hasContext {
  def plus(f: DspComplex[T], g: DspComplex[T]): DspComplex[T] = {
    DspComplex.wire(f.real + g.real, f.imag + g.imag)
  }
  def plusContext(f: DspComplex[T], g: DspComplex[T]): DspComplex[T] = {
    DspComplex.wire(f.real context_+ g.real, f.imag context_+ g.imag)
  }

  /**
    * The builtin times calls +. Ideally we'd like to use growing addition, but we're relying on typeclasses and the
    * default + for UInt, SInt, etc. is wrapping. Thus, we're making an escape hatch just for the default (non-context)
    * complex multiply.
    * @param l
    * @param r
    * @return the sum of l and r, preferrably growing
    */
  protected def plusForTimes(l: T, r: T): T

  def times(f: DspComplex[T], g: DspComplex[T]): DspComplex[T] = {
    val c_p_d = g.real + g.imag
    val a_p_b = f.real + f.imag
    val b_m_a = f.imag - f.real
    val ac_p_ad = f.real * c_p_d
    val ad_p_bd = a_p_b * g.imag
    val bc_m_ac = b_m_a * g.real
    DspComplex.wire(ac_p_ad - ad_p_bd, ac_p_ad + bc_m_ac)
  }
  def timesContext(f: DspComplex[T], g: DspComplex[T]): DspComplex[T] = {
    if (context.complexUse4Muls)
      DspComplex.wire(
        (f.real context_* g.real) context_- (f.imag context_* g.imag),
        (f.real context_* g.imag) context_+ (f.imag context_* g.real)
      )
    else {
      val fRealDly = ShiftRegister(f.real, context.numAddPipes)
      val gRealDly = ShiftRegister(g.real, context.numAddPipes)
      val gImagDly = ShiftRegister(g.imag, context.numAddPipes)
      val c_p_d = g.real context_+ g.imag
      val a_p_b = f.real context_+ f.imag
      val b_m_a = f.imag context_- f.real
      val ac_p_ad = fRealDly context_* c_p_d
      val ad_p_bd = a_p_b context_* gImagDly
      val bc_m_ac = b_m_a context_* gRealDly
      DspComplex.wire(ac_p_ad context_- ad_p_bd, ac_p_ad context_+ bc_m_ac)
    }
  }
  def one: DspComplex[T] = DspComplex(Ring[T].one, Ring[T].zero)
  // Only assigns real part as x
  override def fromInt(x: Int): DspComplex[T] = DspComplex(Ring[T].fromInt(x), Ring[T].zero)
  def zero: DspComplex[T] = DspComplex(Ring[T].zero, Ring[T].zero)
  def negate(f: DspComplex[T]): DspComplex[T] = DspComplex.wire(-f.real, -f.imag)
  def negateContext(f: DspComplex[T]): DspComplex[T] = {
    DspComplex.wire(f.real.context_unary_-, f.imag.context_unary_-)
  }
  override def minus(f: DspComplex[T], g: DspComplex[T]): DspComplex[T] = {
    DspComplex.wire(f.real - g.real, f.imag - g.imag)
  }
  def minusContext(f: DspComplex[T], g: DspComplex[T]): DspComplex[T] = {
    DspComplex.wire(f.real context_- g.real, f.imag context_- g.imag)
  }
}

class DspComplexRingUInt extends DspComplexRing[UInt] {
  override def plusForTimes(l: UInt, r: UInt): UInt = l +& r
}

class DspComplexRingSInt extends DspComplexRing[SInt] {
  override def plusForTimes(l: SInt, r: SInt): SInt = l +& r
}

class DspComplexRingFixed extends DspComplexRing[FixedPoint] {
  override def plusForTimes(l: FixedPoint, r: FixedPoint): FixedPoint = l +& r
}

class DspComplexRingData[T <: Data : Ring] extends DspComplexRing[T] {
  override protected def plusForTimes(l: T, r: T): T = l + r
}

class DspComplexEq[T <: Data:Eq] extends Eq[DspComplex[T]] with hasContext {
  override def eqv(x: DspComplex[T], y: DspComplex[T]): Bool = {
    Eq[T].eqv(x.real, y.real) && Eq[T].eqv(x.imag, y.imag)
  }
  override def neqv(x: DspComplex[T], y: DspComplex[T]): Bool = {
    Eq[T].neqv(x.real, y.real) || Eq[T].neqv(x.imag, y.imag)
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
  def clip(a: DspComplex[T], b: DspComplex[T]): DspComplex[T] = throw DspException("Can't clip on complex")
  def signBit(a: DspComplex[T]): Bool = throw DspException("Can't get sign bit on complex")
  def trimBinary(a: DspComplex[T], n: Option[Int]): DspComplex[T] = 
    DspComplex.wire(BinaryRepresentation[T].trimBinary(a.real, n), BinaryRepresentation[T].trimBinary(a.imag, n))
}

trait GenericDspComplexImpl {
  implicit def DspComplexRingDataImpl[T<: Data:Ring] = new DspComplexRingData[T]()
  implicit def DspComplexEq[T <: Data:Eq] = new DspComplexEq[T]()
  implicit def DspComplexBinaryRepresentation[T <: Data:Ring:BinaryRepresentation] = 
    new DspComplexBinaryRepresentation[T]()
}

trait DspComplexImpl extends GenericDspComplexImpl {
  implicit def DspComplexRingUIntImpl = new DspComplexRingUInt
  implicit def DspComplexRingSIntImpl = new DspComplexRingSInt
  implicit def DspComplexRingFixedImpl = new DspComplexRingFixed
}
