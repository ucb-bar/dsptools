// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3._
import chisel3.util.{Cat, ShiftRegister}
import dsptools.{DspContext, NoTrim, hasContext}
import chisel3.experimental.FixedPoint
import chisel3.internal.firrtl.KnownBinaryPoint

import scala.language.implicitConversions

trait DspRealRing extends Any with Ring[DspReal] with hasContext {
  def one: DspReal = DspReal(1.0)
  def zero: DspReal = DspReal(0.0)
  def plus(f: DspReal, g: DspReal): DspReal = f + g
  def plusContext(f: DspReal, g: DspReal): DspReal = {
    ShiftRegister(f + g, context.numAddPipes)
  }
  override def minus(f: DspReal, g: DspReal): DspReal = f - g
  def minusContext(f: DspReal, g: DspReal): DspReal = {
    ShiftRegister(f - g, context.numAddPipes)
  }
  def negate(f: DspReal): DspReal = minus(zero, f)
  def negateContext(f: DspReal): DspReal = minusContext(zero, f)
  def times(f: DspReal, g: DspReal): DspReal = f * g
  def timesContext(f: DspReal, g: DspReal): DspReal = {
    ShiftRegister(f * g, context.numMulPipes)
  }
}

trait DspRealOrder extends Any with Order[DspReal] with hasContext {
  override def compare(x: DspReal, y: DspReal): ComparisonBundle = {
    ComparisonHelper(x === y, x < y)
  }
  override def eqv(x: DspReal, y: DspReal): Bool = x === y
  override def neqv(x: DspReal, y:DspReal): Bool = x != y
  override def lt(x: DspReal, y: DspReal): Bool = x < y
  override def lteqv(x: DspReal, y: DspReal): Bool = x <= y
  override def gt(x: DspReal, y: DspReal): Bool = x > y
  override def gteqv(x: DspReal, y: DspReal): Bool = x >= y
  // min, max depends on lt, gt & mux
}

trait DspRealSigned extends Any with Signed[DspReal] with DspRealRing with hasContext {
  def signum(a: DspReal): ComparisonBundle = {
    ComparisonHelper(a === DspReal(0.0), a < DspReal(0.0))
  }
  def abs(a: DspReal): DspReal = a.abs()
  def context_abs(a: DspReal): DspReal = {
    Mux(
      isSignNonNegative(ShiftRegister(a, context.numAddPipes)),
      ShiftRegister(a, context.numAddPipes),
      super[DspRealRing].minusContext(DspReal(0.0), a)
    )
  }

  override def isSignZero(a: DspReal): Bool = a === DspReal(0.0)
  override def isSignNegative(a:DspReal): Bool = a < DspReal(0.0)
  // isSignPositive, isSignNonZero, isSignNonPositive, isSignNonNegative derived from above (!)
}

trait DspRealIsReal extends Any with IsReal[DspReal] with DspRealOrder with DspRealSigned with hasContext {
  def ceil(a: DspReal): DspReal = {
    a.ceil()
  }
  def context_ceil(a: DspReal): DspReal = {
    ShiftRegister(a, context.numAddPipes).ceil()
  }
  def floor(a: DspReal): DspReal = a.floor()
  def isWhole(a: DspReal): Bool = a === round(a)
  // Round *half up* -- Different from System Verilog definition! (where half is rounded away from zero)
  // according to 5.7.2 (http://www.ece.uah.edu/~gaede/cpe526/2012%20System%20Verilog%20Language%20Reference%20Manual.pdf)
  def round(a: DspReal): DspReal = a.round()
  def truncate(a: DspReal): DspReal = {
    Mux(ShiftRegister(a, context.numAddPipes) < DspReal(0.0), context_ceil(a), floor(ShiftRegister(a, context.numAddPipes)))
  }
}

trait ConvertableToDspReal extends ConvertableTo[DspReal] with hasContext {
  def fromShort(n: Short): DspReal = fromInt(n.toInt)
  def fromByte(n: Byte): DspReal = fromInt(n.toInt)
  def fromInt(n: Int): DspReal = fromBigInt(BigInt(n))
  def fromFloat(n: Float): DspReal = fromDouble(n.toDouble)
  def fromBigDecimal(n: BigDecimal): DspReal = fromDouble(n.doubleValue)
  def fromLong(n: Long): DspReal = fromBigInt(BigInt(n))
  def fromType[B](n: B)(implicit c: ConvertableFrom[B]): DspReal = fromDouble(c.toDouble(n))
  def fromBigInt(n: BigInt): DspReal = DspReal(n.doubleValue)
  def fromDouble(n: Double): DspReal = DspReal(n) 
  override def fromDouble(d: Double, a: DspReal): DspReal = fromDouble(d)
  // Ignores width
  override def fromDoubleWithFixedWidth(d: Double, a: DspReal): DspReal = fromDouble(d)
}

trait ConvertableFromDspReal extends ChiselConvertableFrom[DspReal] with hasContext {
  // intPart requires truncate, asFixed requires round
  def asReal(a: DspReal): DspReal = a
}

trait BinaryRepresentationDspReal extends BinaryRepresentation[DspReal] with hasContext {
  def shl(a: DspReal, n: Int): DspReal = a * DspReal(math.pow(2, n))
  def shl(a: DspReal, n: UInt): DspReal = {
    require(n.widthKnown, "n Width must be known for shl with DspReal")
    val max = (1 << n.getWidth) - 1
    val lut = VecInit((0 to max).map(x => DspReal(math.pow(2, x))))
    a * lut(n)
  }
  def shr(a: DspReal, n: Int): DspReal = div2(a, n)
  def shr(a: DspReal, n: UInt): DspReal = {
    require(n.widthKnown, "n Width must be known for shr with DspReal")
    val max = (1 << n.getWidth) - 1
    val lut = VecInit((0 to max).map(x => DspReal(math.pow(2.0, -x))))
    a * lut(n)
  }

  def clip(a: DspReal, b: DspReal): DspReal = a

  // mul2 consistent with shl
  // signBit relies on Signed
  override def div2(a: DspReal, n: Int): DspReal = a / DspReal(math.pow(2, n))
  // Used purely for fixed point precision adjustment -- just passes DspReal through
  def trimBinary(a: DspReal, n: Option[Int]): DspReal = a
 }

trait DspRealReal extends DspRealRing with DspRealIsReal with ConvertableToDspReal with
    ConvertableFromDspReal with BinaryRepresentationDspReal with RealBits[DspReal] with hasContext {
  def signBit(a: DspReal): Bool = isSignNegative(a)
  override def fromInt(n: Int): DspReal = super[ConvertableToDspReal].fromInt(n)
  override def fromBigInt(n: BigInt): DspReal = super[ConvertableToDspReal].fromBigInt(n)
  def intPart(a: DspReal): SInt = truncate(a).toSInt()
  // WARNING: Beware of overflow(!)
  def asFixed(a: DspReal, proto: FixedPoint): FixedPoint = {
    require(proto.binaryPoint.known, "Binary point must be known for DspReal -> FixedPoint")
    val bp = proto.binaryPoint.get
    // WARNING: Round half up!
    val out = Wire(proto.cloneType)
    out := DspContext.withTrimType(NoTrim) {
      // round is round half up
      round(a * DspReal((1 << bp).toDouble)).toSInt().asFixed.div2(bp)
    }
    out
  }
}

trait DspRealImpl  {
  implicit object DspRealRealImpl extends DspRealReal
}
