// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3._
import chisel3.experimental.FixedPoint
import chisel3.internal.firrtl.KnownBinaryPoint
import chisel3.util.ShiftRegister
import dsptools._

import scala.language.implicitConversions

//scalastyle:off method.name

/**
  * Defines basic math functions for FixedPoint numbers
  */
trait FixedPointRing extends Any with Ring[FixedPoint] with hasContext {
  def zero: FixedPoint = 0.0.F(0.BP)
  def one: FixedPoint= 1.0.F(0.BP)
  def plus(f: FixedPoint, g: FixedPoint): FixedPoint = f + g
  def plusContext(f: FixedPoint, g: FixedPoint): FixedPoint = {
    // TODO: Saturating mux should be outside of ShiftRegister
    val sum = context.overflowType match {
      case Grow => f +& g
      case Wrap => f +% g
      case _ => throw DspException("Saturating add hasn't been implemented")
    }
    ShiftRegister(sum, context.numAddPipes)
  }
  override def minus(f: FixedPoint, g: FixedPoint): FixedPoint = f - g
  def minusContext(f: FixedPoint, g: FixedPoint): FixedPoint = {
    val diff = context.overflowType match {
      case Grow => f -& g
      case Wrap => f -% g
      case _ => throw DspException("Saturating subtractor hasn't been implemented")
    }
    ShiftRegister(diff, context.numAddPipes)
  }
  def negate(f: FixedPoint): FixedPoint = -f
  def negateContext(f: FixedPoint): FixedPoint = minus(zero, f)

  def times(f: FixedPoint, g: FixedPoint): FixedPoint = f * g

  // timesContext moved later b/c need trim binary
}

trait FixedPointOrder extends Any with Order[FixedPoint] with hasContext {
  override def compare(x: FixedPoint, y: FixedPoint): ComparisonBundle = {
    ComparisonHelper(x === y, x < y)
  }
  override def eqv(x: FixedPoint, y: FixedPoint): Bool = x === y
  override def neqv(x: FixedPoint, y:FixedPoint): Bool = x =/= y
  override def lt(x: FixedPoint, y: FixedPoint): Bool = x < y
  override def lteqv(x: FixedPoint, y: FixedPoint): Bool = x <= y
  override def gt(x: FixedPoint, y: FixedPoint): Bool = x > y
  override def gteqv(x: FixedPoint, y: FixedPoint): Bool = x >= y
  // min, max depends on lt, gt & mux
}

trait FixedPointSigned extends Any with Signed[FixedPoint] with hasContext {
  // isSignPositive, isSignNonZero, isSignNonPositive, isSignNonNegative derived from above (!)
  // abs requires ring (for overflow) so overridden later
  // isSignZero, isSignNegative moved to FixedPointReal to get access to 'zero'
}

trait FixedPointIsReal extends Any with IsReal[FixedPoint] with FixedPointOrder with FixedPointSigned with hasContext {
  // Chop off fractional bits --> round to negative infinity
  def floor(a: FixedPoint): FixedPoint = a.setBinaryPoint(0)
  def isWhole(a: FixedPoint): Bool = a === floor(a)
  // Truncate = round towards zero (integer part without fractional bits)
  def truncate(a: FixedPoint): FixedPoint = {
    Mux(isSignNegative(ShiftRegister(a, context.numAddPipes)),
      ceil(a),
      floor(ShiftRegister(a, context.numAddPipes))
    )
  }
  // ceil, round moved to FixedPointReal to get access to ring
}

trait ConvertableToFixedPoint extends ConvertableTo[FixedPoint] with hasContext {
  def fromShort(n: Short): FixedPoint = fromInt(n.toInt)
  def fromByte(n: Byte): FixedPoint = fromInt(n.toInt)
  def fromInt(n: Int): FixedPoint = fromBigInt(BigInt(n))
  def fromFloat(n: Float): FixedPoint = fromDouble(n.toDouble)
  def fromBigDecimal(n: BigDecimal): FixedPoint = fromDouble(n.doubleValue)
  def fromLong(n: Long): FixedPoint = fromBigInt(BigInt(n))
  def fromType[B](n: B)(implicit c: ConvertableFrom[B]): FixedPoint = fromDouble(c.toDouble(n))
  def fromBigInt(n: BigInt): FixedPoint = n.doubleValue.F(0.BP)
  // If no binary point is specified, use the default one provided by DspContext
  // TODO: Should you instead be specifying a max width so you can get the most resolution for a given width?
  def fromDouble(n: Double): FixedPoint = n.F(context.binaryPoint.getOrElse(0).BP)
  override def fromDouble(d: Double, a: FixedPoint): FixedPoint = {
    require(a.binaryPoint.known, "Binary point must be known!")
    d.F(a.binaryPoint)
  }
  override def fromDoubleWithFixedWidth(d: Double, a: FixedPoint): FixedPoint = {
    require(a.binaryPoint.known, "Binary point must be known!")
    require(a.widthKnown, "FixedPoint width not known!")
    val sintBits = BigInt(d.toInt).bitLength + 1
    require(sintBits + a.binaryPoint.get <= a.getWidth, "Lit can't fit in prototype FixedPoint bitwidth")
    d.F(a.getWidth.W, a.binaryPoint)
  }
}

trait ConvertableFromFixedPoint extends ChiselConvertableFrom[FixedPoint] with hasContext {
  // intPart depends on truncate
  // asReal depends on shifting fractional bits up
  override def asFixed(a: FixedPoint): FixedPoint = a
  def asFixed(a: FixedPoint, proto: FixedPoint): FixedPoint = asFixed(a)
}

trait BinaryRepresentationFixedPoint extends BinaryRepresentation[FixedPoint] with hasContext {
  def shl(a: FixedPoint, n: Int): FixedPoint = a << n
  def shl(a: FixedPoint, n: UInt): FixedPoint = a << n

  // Note: This rounds to negative infinity (smallest abs. value for negative #'s is -LSB)
  def shr(a: FixedPoint, n: Int): FixedPoint = a >> n
  def shr(a: FixedPoint, n: UInt): FixedPoint = a >> n

  // mul2 consistent with shl
  // signBit relies on Signed

  // Retains significant digits while dividing
  override def div2(a: FixedPoint, n: Int): FixedPoint = {
    require(a.widthKnown, "Fixed point width must be known for div2")
    require(a.binaryPoint.known, "Binary point must be known for div2")
    val newBP = a.binaryPoint.get + n
    // Normal shift loses significant digits; this version doesn't
    val inLong = Wire(FixedPoint((a.getWidth + n).W, newBP.BP))
    inLong := a
    val outFull = Wire(FixedPoint(a.getWidth.W, newBP.BP))
    // Upper n bits don't contain meaningful data following shift, so remove
    outFull := inLong >> n
    // Note: The above doesn't rely on tools to expand, shrink correctly; the version below does.
    // Assumes setBinaryPoint zero-extends. BUT Chisel doesn't seem to get widths properly and
    // some other ops rely on width correctness... (even though Firrtl is right...)
    //a.setBinaryPoint(newBP) >> n
    trimBinary(outFull, Some(a.binaryPoint.get + context.binaryPointGrowth))
  }
  // trimBinary below for access to ring ops

 }

trait FixedPointReal extends FixedPointRing with FixedPointIsReal with ConvertableToFixedPoint with
    ConvertableFromFixedPoint with BinaryRepresentationFixedPoint with RealBits[FixedPoint] with hasContext {

  def clip(a: FixedPoint, b: FixedPoint): FixedPoint = ???

  def trimBinary(a: FixedPoint, n: Option[Int]): FixedPoint = {
    // TODO: Support other modes?
    n match {
      case None => a
      case Some(b) => context.trimType match {
        case NoTrim => a
        case RoundDown => a.setBinaryPoint(b)
        case RoundUp => {
          val addAmt = math.pow(2, -b).F(b.BP) // shr(1.0.F(b.BP),b)
          Mux((a === a.setBinaryPoint(b)), a.setBinaryPoint(b), plus(a.setBinaryPoint(b), addAmt))
        }
        case RoundTowardsZero => {
          val addAmt = math.pow(2, -b).F(b.BP) // shr(1.0.F(b.BP),b)
          val valueForNegativeNum = Mux((a === a.setBinaryPoint(b)), a.setBinaryPoint(b), plus(a.setBinaryPoint(b), addAmt))
          Mux(isSignNegative(a), valueForNegativeNum, a.setBinaryPoint(b))
        }
        case RoundTowardsInfinity => {
          val addAmt = math.pow(2, -b).F(b.BP) // shr(1.0.F(b.BP),b)
          val valueForPositiveNum = Mux((a === a.setBinaryPoint(b)), a.setBinaryPoint(b), plus(a.setBinaryPoint(b), addAmt))
          Mux(isSignNegative(a), a.setBinaryPoint(b), valueForPositiveNum)
        }
        case RoundHalfDown => {
          val addAmt1 = math.pow(2, -b).F(b.BP) // shr(1.0.F(b.BP),b)
          val addAmt2 = math.pow(2, -(b+1)).F((b+1).BP) // shr(1.0.F((b+1).BP),(b+1))
          Mux((a > plus(a.setBinaryPoint(b), addAmt2)), plus(a.setBinaryPoint(b), addAmt1), a.setBinaryPoint(b))
        }
        case RoundHalfUp => {
          val roundBp = b + 1
          val addAmt = math.pow(2, -roundBp).F(roundBp.BP)
          plus(a, addAmt).setBinaryPoint(b)
        }
        case RoundHalfTowardsZero => {
          val addAmt1 = math.pow(2, -b).F(b.BP) // shr(1.0.F(b.BP),b)
          val addAmt2 = math.pow(2, -(b+1)).F((b+1).BP) // shr(1.0.F((b+1).BP),(b+1))
          val valueForPositiveNum = Mux((a > plus(a.setBinaryPoint(b), addAmt2)), plus(a.setBinaryPoint(b), addAmt1), a.setBinaryPoint(b))
          Mux(isSignNegative(a), plus(a, addAmt2).setBinaryPoint(b), valueForPositiveNum)
        }
        case RoundHalfTowardsInfinity => {
          val roundBp = b + 1
          val addAmt = math.pow(2, -roundBp).F(roundBp.BP)
          Mux(isSignNegative(a) && (a === a.setBinaryPoint(roundBp)), a.setBinaryPoint(b), plus(a, addAmt).setBinaryPoint(b))
        }
        case RoundHalfToEven => {
          require(b > 0, "Binary point of input fixed point number must be larger than zero when trimming")
          val roundBp = b + 1
          val checkIfEvenBp = b - 1
          val addAmt = math.pow(2, -roundBp).F(roundBp.BP)
          Mux((a.setBinaryPoint(checkIfEvenBp) === a.setBinaryPoint(b)) && (a === a.setBinaryPoint(roundBp)), a.setBinaryPoint(b), plus(a, addAmt).setBinaryPoint(b))
        }
        case RoundHalfToOdd => {
          require(b > 0, "Binary point of input fixed point number must be larger than zero when trimming")
          val roundBp = b + 1
          val checkIfOddBp = b - 1
          val addAmt = math.pow(2, -roundBp).F(roundBp.BP)
          Mux((a.setBinaryPoint(checkIfOddBp) =/= a.setBinaryPoint(b)) && (a === a.setBinaryPoint(roundBp)), a.setBinaryPoint(b), plus(a, addAmt).setBinaryPoint(b))
        }
        case _ => throw DspException("Desired trim type not implemented!")
      }
    }
  }

  def timesContext(f: FixedPoint, g: FixedPoint): FixedPoint = {
    // TODO: Overflow via ranging in FIRRTL?
    // Rounding after registering to make retiming easier to recognize
    val outTemp = ShiftRegister(f * g, context.numMulPipes)
    val newBP = (f.binaryPoint, g.binaryPoint) match {
      case (KnownBinaryPoint(i), KnownBinaryPoint(j)) => Some(i.max(j) + context.binaryPointGrowth)
      case (_, _) => None
    }
    trimBinary(outTemp, newBP)
  }

  def signum(a: FixedPoint): ComparisonBundle = {
    ComparisonHelper(a === zero, a < zero)
  }
  override def isSignZero(a: FixedPoint): Bool = a === zero
  override def isSignNegative(a:FixedPoint): Bool = {
    if (a.widthKnown) a(a.getWidth-1)
    else a < zero
  }

  // Can potentially overflow
  def ceil(a: FixedPoint): FixedPoint = {
    Mux(
      isWhole(ShiftRegister(a, context.numAddPipes)),
      floor(ShiftRegister(a, context.numAddPipes)),
      plusContext(floor(a), one))
  }
  def context_ceil(a: FixedPoint): FixedPoint = ceil(a)

  // Round half up: Can potentially overflow [round half towards positive infinity]
  // NOTE: Apparently different from Java for negatives
  def round(a: FixedPoint): FixedPoint = floor(plusContext(a, 0.5.F(1.BP)))

  def signBit(a: FixedPoint): Bool = isSignNegative(a)
  // fromFixedPoint also included in Ring
  override def fromInt(n: Int): FixedPoint = super[ConvertableToFixedPoint].fromInt(n)
  override def fromBigInt(n: BigInt): FixedPoint = super[ConvertableToFixedPoint].fromBigInt(n)
  // Overflow only on most negative
  def abs(a: FixedPoint): FixedPoint = {
    Mux(isSignNegative(a), super[FixedPointRing].minus(zero, a), a)
  }
  def context_abs(a: FixedPoint): FixedPoint = {
    Mux(
      isSignNegative(ShiftRegister(a, context.numAddPipes)),
      super[FixedPointRing].minusContext(zero, a),
      ShiftRegister(a, context.numAddPipes))
  }

  def intPart(a: FixedPoint): SInt = truncate(a).asSInt

  // Converts to DspReal
  def asReal(a: FixedPoint): DspReal = {
    require(a.binaryPoint.known, "Binary point must be known for asReal")
    val n = a.binaryPoint.get
    val normalizedInt = a << n
    DspReal(floor(normalizedInt).asSInt)/DspReal((1 << n).toDouble)
  }
}

trait FixedPointImpl {
  implicit object FixedPointRealImpl extends FixedPointReal
}
