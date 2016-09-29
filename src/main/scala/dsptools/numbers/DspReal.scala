// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import chisel3.core.Wire
import dsptools.DspContext
import spire.algebra.Ring
import spire.math.{UInt => _, _}


class BlackboxOneOperand extends BlackBox {
  val io = new Bundle() {
    val in = UInt(INPUT, DspReal.UnderlyingWidth)
    val out = UInt(OUTPUT, DspReal.UnderlyingWidth)
  }
}

class BlackboxTwoOperand extends BlackBox {
  val io = new Bundle() {
    val in1 = UInt(INPUT, DspReal.UnderlyingWidth)
    val in2 = UInt(INPUT, DspReal.UnderlyingWidth)
    val out = UInt(OUTPUT, DspReal.UnderlyingWidth)
  }
}

class BlackboxTwoOperandBool extends BlackBox {
  val io = new Bundle() {
    val in1 = UInt(INPUT, DspReal.UnderlyingWidth)
    val in2 = UInt(INPUT, DspReal.UnderlyingWidth)
    val out = Bool(OUTPUT)
  }
}

class BBFAdd extends BlackboxTwoOperand

class BBFSubtract extends BlackboxTwoOperand

class BBFMultiply extends BlackboxTwoOperand

class BBFDivide extends BlackboxTwoOperand

class BBFGreaterThan extends BlackboxTwoOperandBool

class BBFGreaterThanEquals extends BlackboxTwoOperandBool

class BBFLessThan extends BlackboxTwoOperandBool

class BBFLessThanEquals extends BlackboxTwoOperandBool

class BBFEquals extends BlackboxTwoOperandBool

class BBFNotEquals extends BlackboxTwoOperandBool

class BBFFromInt extends BlackBox {
  val io = new Bundle() {
    val in = UInt(INPUT, DspReal.UnderlyingWidth)
    val out = UInt(OUTPUT, DspReal.UnderlyingWidth)
  }
}

class BBFToInt extends BlackBox {
  val io = new Bundle() {
    val in = UInt(INPUT, DspReal.UnderlyingWidth)
    val out = UInt(OUTPUT, DspReal.UnderlyingWidth)
  }
}

class BBFIntPart extends BlackboxOneOperand

class DspReal extends Bundle {
  val node = UInt(OUTPUT, DspReal.UnderlyingWidth)

  private def oneOperandOperator(blackbox_gen: => BlackboxOneOperand) : DspReal = {
    val blackbox = blackbox_gen
    blackbox.io.in := node
    val out = Wire(new DspReal)
    out.node := blackbox.io.out
    out
  }

  private def twoOperandOperator(arg1: DspReal, blackbox_gen: => BlackboxTwoOperand) : DspReal = {
    val blackbox = blackbox_gen
    blackbox.io.in1 := node
    blackbox.io.in2 := arg1.node
    val out = Wire(new DspReal)
    out.node := blackbox.io.out
    out
  }

  private def twoOperandBool(arg1: DspReal, blackbox_gen: => BlackboxTwoOperandBool) : Bool = {
    val blackbox = blackbox_gen
    blackbox.io.in1 := node
    blackbox.io.in2 := arg1.node
    val out = Wire(new Bool(OUTPUT))
    out := blackbox.io.out
    out
  }

  def + (arg1: DspReal): DspReal = {
    twoOperandOperator(arg1, Module(new BBFAdd()))
  }

  def - (arg1: DspReal): DspReal = {
    twoOperandOperator(arg1, Module(new BBFSubtract()))
  }

  def * (arg1: DspReal): DspReal = {
    twoOperandOperator(arg1, Module(new BBFMultiply()))
  }

  def / (arg1: DspReal): DspReal = {
    twoOperandOperator(arg1, Module(new BBFDivide()))
  }

  def > (arg1: DspReal): Bool = {
    twoOperandBool(arg1, Module(new BBFGreaterThan()))
  }

  def >= (arg1: DspReal): Bool = {
    twoOperandBool(arg1, Module(new BBFGreaterThanEquals()))
  }

  def < (arg1: DspReal): Bool = {
    twoOperandBool(arg1, Module(new BBFLessThan()))
  }

  def <= (arg1: DspReal): Bool = {
    twoOperandBool(arg1, Module(new BBFLessThanEquals()))
  }

  def === (arg1: DspReal): Bool = {
    twoOperandBool(arg1, Module(new BBFEquals()))
  }

  def != (arg1: DspReal): Bool = {
    twoOperandBool(arg1, Module(new BBFNotEquals()))
  }

  def intPart(dummy: Int = 0): DspReal = {
    oneOperandOperator(Module(new BBFIntPart()))
  }
  /** Returns this Real's value truncated to an integer, as a DspReal.UnderlyingWidth-bit UInt.
    * Behavior on overflow (possible with large exponent terms) is undefined.
    */
  def toUInt(dummy: Int = 0): UInt = {
    val blackbox = Module(new BBFToInt)
    blackbox.io.in := node
    blackbox.io.out
  }

  /** Returns this Real's value as its bit representation in DspReal.UnderlyingWidth-bit floating point.
    */
  def toDoubleBits(dummy: Int = 0): UInt = {
    node
  }
}

object DspReal {
  val UnderlyingWidth = 64
  /** Creates a Real with a constant value.
    */
  def apply(value: Double): DspReal = {
    def longAsUnsignedBigInt(in: Long) = (BigInt(in >>> 1) << 1) + (in & 1)
    def doubleToBits(in: Double) = longAsUnsignedBigInt(java.lang.Double.doubleToRawLongBits(value))
    val out = Wire(new DspReal)
    out.node := UInt(doubleToBits(value), width=DspReal.UnderlyingWidth)
    out
  }

  /**
    * Creates a Real by doing integer conversion from a (up to) DspReal.UnderlyingWidth-bit UInt.
    */
  def apply(value: UInt): DspReal = {
    val blackbox = Module(new BBFFromInt)
    blackbox.io.in := value
    val out = Wire(new DspReal)
    out.node := blackbox.io.out
    out
  }
}

trait DspRealRing extends Any with Ring[DspReal] with hasContext {
  def plus(f: DspReal, g: DspReal): DspReal = {
    f + g
  }
  def times(f: DspReal, g: DspReal): DspReal = {
    f * g
  }
//  def one: DspReal = DspReal.wire(DspReal(1.0))
//  def zero: DspReal = DspReal.wire(DspReal(0.0))
  def one: DspReal = DspReal(1.0)
  def zero: DspReal = DspReal(0.0)
  def negate(f: DspReal): DspReal = zero - f
}

trait DspRealImpl {
  implicit object DspRealIntegralImpl extends DspRealIntegral
}

trait DspRealOrder extends Any with Order[DspReal] with hasContext {
  override def compare(x: DspReal, y: DspReal): ComparisonBundle = {
    ComparisonHelper(x === y, x < y)
  }
}

trait DspRealSigned extends Any with Signed[DspReal] with hasContext {
  def signum(a: DspReal): ComparisonBundle = {
    ComparisonHelper(a === DspReal(0), a < DspReal(0))
  }

  /** An idempotent function that ensures an object has a non-negative sign. */
  def abs(a: DspReal): DspReal = Mux(a > DspReal(0), a, DspReal(0)-a)
}
trait DspRealIsReal extends Any with IsIntegral[DspReal] with DspRealOrder with DspRealSigned with hasContext {
  def toDouble(a: DspReal): DspReal = ???
}

trait ConvertableToDspReal extends ConvertableTo[DspReal] with hasContext {
  def fromShort(n: Short): DspReal = DspReal(n.toInt)
  def fromAlgebraic(n: Algebraic): DspReal = DspReal(n.toInt)
  def fromBigInt(n: BigInt): DspReal = DspReal(n.toInt)
  def fromByte(n: Byte): DspReal = DspReal(n.toInt)
  def fromDouble(n: Double): DspReal = DspReal(n)
  def fromReal(n: Real): DspReal = DspReal(n.toDouble)
  def fromRational(n: Rational): DspReal = DspReal(n.toDouble)
  def fromType[B](n: B)(implicit c: ConvertableFrom[B]): DspReal = DspReal(c.toDouble(n))
  def fromInt(n: Int): DspReal = DspReal(n)
  def fromFloat(n: Float): DspReal = DspReal(n.toDouble)
  def fromBigDecimal(n: BigDecimal): DspReal = DspReal(n.toDouble)
  def fromLong(n: Long): DspReal = DspReal(n)
}

trait DspRealIntegral extends DspRealRing with ConvertableToDspReal with DspRealIsReal with Integral[DspReal] with hasContext {
  override def fromInt(n: Int): DspReal = super[DspRealRing].fromInt(n)
}
