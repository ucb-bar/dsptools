// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers.chisel_types

import chisel3._
import chisel3.experimental.{FixedPoint, Interval}
import chisel3.internal.firrtl.KnownBinaryPoint
import chisel3.util.ShiftRegister
import dsptools.numbers.{
  BinaryRepresentation, ChiselConvertableFrom, ComparisonBundle, ComparisonHelper,
  ConvertableFrom, ConvertableTo, DspReal, IsReal, Order, RealBits, Ring, Signed
}
import dsptools._

import scala.language.implicitConversions

//scalastyle:off method.name

/**
  * Defines basic math functions for Interval numbers
  */
trait IntervalRing extends Any with Ring[Interval] with hasContext {
  def zero: Interval = Interval.Zero
  def one: Interval= 1.0.I(0.BP)
  def plus(f: Interval, g: Interval): Interval = f + g
  def plusContext(f: Interval, g: Interval): Interval = {
    // TODO: Saturating mux should be outside of ShiftRegister
    val sum = context.overflowType match {
      case Grow => f +& g
      case Wrap => f +% g
      case _ => throw DspException("Saturating add hasn't been implemented")
    }
    ShiftRegister(sum, context.numAddPipes)
  }
  override def minus(f: Interval, g: Interval): Interval = f - g
  def minusContext(f: Interval, g: Interval): Interval = {
    val diff = context.overflowType match {
      case Grow => f -& g
      case Wrap => f -% g
      case _ => throw DspException("Saturating subtractor hasn't been implemented")
    }
    ShiftRegister(diff, context.numAddPipes)
  }
  def negate(f: Interval): Interval = -(f)
  def negateContext(f: Interval): Interval = minus(zero, f)

  def times(f: Interval, g: Interval): Interval = f * g

  // timesContext moved later b/c need trim binary
}

trait IntervalOrder extends Any with Order[Interval] with hasContext {
  override def compare(x: Interval, y: Interval): ComparisonBundle = {
    ComparisonHelper(x === y, x < y)
  }
  override def eqv(x: Interval, y: Interval): Bool = x === y
  override def neqv(x: Interval, y:Interval): Bool = x =/= y
  override def lt(x: Interval, y: Interval): Bool = x < y
  override def lteqv(x: Interval, y: Interval): Bool = x <= y
  override def gt(x: Interval, y: Interval): Bool = x > y
  override def gteqv(x: Interval, y: Interval): Bool = x >= y
  // min, max depends on lt, gt & mux
}

trait IntervalSigned extends Any with Signed[Interval] with hasContext {
  // isSignPositive, isSignNonZero, isSignNonPositive, isSignNonNegative derived from above (!)
  // abs requires ring (for overflow) so overridden later
  // isSignZero, isSignNegative moved to IntervalReal to get access to 'zero'
}

trait IntervalIsReal extends Any with IsReal[Interval] with IntervalOrder with IntervalSigned with hasContext {
  // Chop off fractional bits --> round to negative infinity
  def floor(a: Interval): Interval = a.setPrecision(0)
  def isWhole(a: Interval): Bool = a === floor(a)
  // Truncate = round towards zero (integer part without fractional bits)
  def truncate(a: Interval): Interval = {
    Mux(isSignNegative(ShiftRegister(a, context.numAddPipes)),
      ceil(a),
      floor(ShiftRegister(a, context.numAddPipes))
    )
  }
  // ceil, round moved to IntervalReal to get access to ring
}

trait ConvertableToInterval extends ConvertableTo[Interval] with hasContext {
  def fromShort(n: Short): Interval = fromInt(n.toInt)
  def fromByte(n: Byte): Interval = fromInt(n.toInt)
  def fromInt(n: Int): Interval = fromBigInt(BigInt(n))
  def fromFloat(n: Float): Interval = fromDouble(n.toDouble)
  def fromBigDecimal(n: BigDecimal): Interval = fromDouble(n.doubleValue)
  def fromLong(n: Long): Interval = fromBigInt(BigInt(n))
  def fromType[B](n: B)(implicit c: ConvertableFrom[B]): Interval = fromDouble(c.toDouble(n))
  def fromBigInt(n: BigInt): Interval = n.doubleValue.I(0.BP)
  // If no binary point is specified, use the default one provided by DspContext
  // TODO: Should you instead be specifying a max width so you can get the most resolution for a given width?
  def fromDouble(n: Double): Interval = n.I(context.binaryPoint.getOrElse(0).BP)
  override def fromDouble(d: Double, a: Interval): Interval = {
    require(a.binaryPoint.known, "Binary point must be known!")
    d.I(a.binaryPoint)
  }
  override def fromDoubleWithFixedWidth(d: Double, a: Interval): Interval = {
    require(a.binaryPoint.known, "Binary point must be known!")
    require(a.widthKnown, "Interval width not known!")
    val sintBits = BigInt(d.toInt).bitLength + 1
    require(sintBits + a.binaryPoint.get <= a.getWidth, "Lit can't fit in prototype Interval bitwidth")
    d.I(a.getWidth.W, a.binaryPoint)
  }
}

trait ConvertableFromInterval extends ChiselConvertableFrom[Interval] with hasContext {
  // intPart depends on truncate
  // asReal depends on shifting fractional bits up

  override def asFixed(a: Interval): FixedPoint = a.asFixedPoint(a.binaryPoint)
  def asFixed(a: Interval, proto: FixedPoint): FixedPoint = a.asFixedPoint(proto.binaryPoint)

  override def asInterval(a: Interval): Interval = a
  def asInterval(a: Interval, proto: Interval): Interval = asInterval(a)
}

trait BinaryRepresentationInterval extends BinaryRepresentation[Interval] with hasContext {
  def shl(a: Interval, n: Int): Interval = a << n
  def shl(a: Interval, n: UInt): Interval = a << n

  // Note: This rounds to negative infinity (smallest abs. value for negative #'s is -LSB)
  def shr(a: Interval, n: Int): Interval = a >> n
  def shr(a: Interval, n: UInt): Interval = a >> n

  // mul2 consistent with shl
  // signBit relies on Signed

  // Retains significant digits while dividing
  override def div2(a: Interval, n: Int): Interval = {
    require(a.widthKnown, "Interval point width must be known for div2")
    require(a.binaryPoint.known, "Binary point must be known for div2")
    val newBP = a.binaryPoint.get + n
    // Normal shift loses significant digits; this version doesn't
    val inLong = Wire(Interval((a.getWidth + n).W, newBP.BP))
    inLong := a
    val outFull = Wire(Interval(a.getWidth.W, newBP.BP))
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

trait IntervalReal extends IntervalRing with IntervalIsReal with ConvertableToInterval with
    ConvertableFromInterval with BinaryRepresentationInterval with RealBits[Interval] with hasContext {

  def clip(a: Interval, b: Interval): Interval = ???

  def trimBinary(a: Interval, n: Option[Int]): Interval = {
    // TODO: Support other modes?
    n match {
      case None => a
      case Some(b) => context.trimType match {
        case NoTrim => a
        case RoundDown => a.setPrecision(b)
        case RoundUp => {
          val addAmt = math.pow(2, -b).I(b.BP) // shr(1.0.I(b.BP),b)
          Mux((a === a.setPrecision(b)), a.setPrecision(b), plus(a.setPrecision(b), addAmt))
        }
        case RoundTowardsZero => {
          val addAmt = math.pow(2, -b).I(b.BP) // shr(1.0.I(b.BP),b)
          val valueForNegativeNum = Mux((a === a.setPrecision(b)), a.setPrecision(b), plus(a.setPrecision(b), addAmt))
          Mux(isSignNegative(a), valueForNegativeNum, a.setPrecision(b))
        }
        case RoundTowardsInfinity => {
          val addAmt = math.pow(2, -b).I(b.BP) // shr(1.0.I(b.BP),b)
          val valueForPositiveNum = Mux((a === a.setPrecision(b)), a.setPrecision(b), plus(a.setPrecision(b), addAmt))
          Mux(isSignNegative(a), a.setPrecision(b), valueForPositiveNum)
        }
        case RoundHalfDown => {
          val addAmt1 = math.pow(2, -b).I(b.BP) // shr(1.0.I(b.BP),b)
          val addAmt2 = math.pow(2, -(b+1)).I((b+1).BP) // shr(1.0.I((b+1).BP),(b+1))
          Mux((a > plus(a.setPrecision(b), addAmt2)), plus(a.setPrecision(b), addAmt1), a.setPrecision(b))
        }
        case RoundHalfUp => {
          val roundBp = b + 1
          val addAmt = math.pow(2, -roundBp).I(roundBp.BP)
          plus(a, addAmt).setPrecision(b)
        }
        case RoundHalfTowardsZero => {
          val addAmt1 = math.pow(2, -b).I(b.BP) // shr(1.0.I(b.BP),b)
          val addAmt2 = math.pow(2, -(b+1)).I((b+1).BP) // shr(1.0.I((b+1).BP),(b+1))
          val valueForPositiveNum = Mux((a > plus(a.setPrecision(b), addAmt2)), plus(a.setPrecision(b), addAmt1), a.setPrecision(b))
          Mux(isSignNegative(a), plus(a, addAmt2).setPrecision(b), valueForPositiveNum)
        }
        case RoundHalfTowardsInfinity => {
          val roundBp = b + 1
          val addAmt = math.pow(2, -roundBp).I(roundBp.BP)
          Mux(isSignNegative(a) && (a === a.setPrecision(roundBp)), a.setPrecision(b), plus(a, addAmt).setPrecision(b))
        }
        case RoundHalfToEven => {
          require(b > 0, "Binary point of input fixed point number must be larger than zero when trimming")
          val roundBp = b + 1
          val checkIfEvenBp = b - 1
          val addAmt = math.pow(2, -roundBp).I(roundBp.BP)
          Mux((a.setPrecision(checkIfEvenBp) === a.setPrecision(b)) && (a === a.setPrecision(roundBp)), a.setPrecision(b), plus(a, addAmt).setPrecision(b))
        }
        case RoundHalfToOdd => {
          require(b > 0, "Binary point of input fixed point number must be larger than zero when trimming")
          val roundBp = b + 1
          val checkIfOddBp = b - 1
          val addAmt = math.pow(2, -roundBp).I(roundBp.BP)
          Mux((a.setPrecision(checkIfOddBp) =/= a.setPrecision(b)) && (a === a.setPrecision(roundBp)), a.setPrecision(b), plus(a, addAmt).setPrecision(b))
        }
        case _ => throw DspException("Desired trim type not implemented!")
      }
    }
  }

  def timesContext(f: Interval, g: Interval): Interval = {
    // TODO: Overflow via ranging in FIRRTL?
    // Rounding after registering to make retiming easier to recognize
    val outTemp = ShiftRegister(f * g, context.numMulPipes)
    val newBP = (f.binaryPoint, g.binaryPoint) match {
      case (KnownBinaryPoint(i), KnownBinaryPoint(j)) => Some(i.max(j) + context.binaryPointGrowth)
      case (_, _) => None
    }
    trimBinary(outTemp, newBP)
  }

  def signum(a: Interval): ComparisonBundle = {
    ComparisonHelper(a === zero, a < zero)
  }
  override def isSignZero(a: Interval): Bool = a === zero
  override def isSignNegative(a:Interval): Bool = {
    if (a.widthKnown) a(a.getWidth-1)
    else a < zero
  }

  // Can potentially overflow
  def ceil(a: Interval): Interval = {
    Mux(
      isWhole(ShiftRegister(a, context.numAddPipes)),
      floor(ShiftRegister(a, context.numAddPipes)),
      plusContext(floor(a), one))
  }
  def context_ceil(a: Interval): Interval = ceil(a)

  // Round half up: Can potentially overflow [round half towards positive infinity]
  // NOTE: Apparently different from Java for negatives
  def round(a: Interval): Interval = floor(plusContext(a, 0.5.I(1.BP)))

  def signBit(a: Interval): Bool = isSignNegative(a)
  // fromInterval also included in Ring
  override def fromInt(n: Int): Interval = super[ConvertableToInterval].fromInt(n)
  override def fromBigInt(n: BigInt): Interval = super[ConvertableToInterval].fromBigInt(n)
  // Overflow only on most negative
  def abs(a: Interval): Interval = {
    Mux(isSignNegative(a), super[IntervalRing].minus(zero, a), a)
  }
  def context_abs(a: Interval): Interval = {
    Mux(
      isSignNegative(ShiftRegister(a, context.numAddPipes)),
      super[IntervalRing].minusContext(zero, a),
      ShiftRegister(a, context.numAddPipes))
  }

  def intPart(a: Interval): SInt = truncate(a).asSInt

  // Converts to DspReal
  def asReal(a: Interval): DspReal = {
    require(a.binaryPoint.known, "Binary point must be known for asReal")
    val n = a.binaryPoint.get
    val normalizedInt = a << n
    DspReal(floor(normalizedInt).asSInt)/DspReal((1 << n).toDouble)
  }
}

trait IntervalImpl {
  implicit object IntervalRealImpl extends IntervalReal
}
