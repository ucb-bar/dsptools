// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import chisel3.core.Wire
import dsptools.hasContext

class BlackboxOneOperand extends BlackBox {
  val io = IO(new Bundle() {
    val in = Input(UInt(DspReal.UnderlyingWidth.W))
    val out = Output(UInt(DspReal.UnderlyingWidth.W))
  })
}

class BlackboxTwoOperand extends BlackBox {
  val io = IO(new Bundle() {
    val in1 = Input(UInt(DspReal.UnderlyingWidth.W))
    val in2 = Input(UInt(DspReal.UnderlyingWidth.W))
    val out = Output(UInt(DspReal.UnderlyingWidth.W))
  })
}

class BlackboxTwoOperandBool extends BlackBox {
  val io = IO(new Bundle() {
    val in1 = Input(UInt(DspReal.UnderlyingWidth.W))
    val in2 = Input(UInt(DspReal.UnderlyingWidth.W))
    val out = Output(Bool())
  })
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

/** Math operations from IEEE.1364-2005 **/
class BBFLn extends BlackboxOneOperand

class BBFLog10 extends BlackboxOneOperand

class BBFExp extends BlackboxOneOperand

class BBFSqrt extends BlackboxOneOperand

class BBFPow extends BlackboxTwoOperand

class BBFFloor extends BlackboxOneOperand

class BBFCeil extends BlackboxOneOperand

class BBFSin extends BlackboxOneOperand

class BBFCos extends BlackboxOneOperand

class BBFTan extends BlackboxOneOperand

class BBFASin extends BlackboxOneOperand

class BBFACos extends BlackboxOneOperand

class BBFATan extends BlackboxOneOperand

class BBFATan2 extends BlackboxTwoOperand

class BBFHypot extends BlackboxTwoOperand

class BBFSinh extends BlackboxOneOperand

class BBFCosh extends BlackboxOneOperand

class BBFTanh extends BlackboxOneOperand

class BBFASinh extends BlackboxOneOperand

class BBFACosh extends BlackboxOneOperand

class BBFATanh extends BlackboxOneOperand

class BBFFromInt extends BlackBox {
  val io = IO(new Bundle() {
    val in = Input(UInt(DspReal.UnderlyingWidth.W))
    val out = Output(UInt(DspReal.UnderlyingWidth.W))
  })
}

class BBFToInt extends BlackBox {
  val io = IO(new Bundle() {
    val in = Input(UInt(DspReal.UnderlyingWidth.W))
    val out = Output(UInt(DspReal.UnderlyingWidth.W))
  })
}

class BBFIntPart extends BlackboxOneOperand

class DspReal(lit: Option[BigInt] = None) extends Bundle {
  val node: UInt = lit match {
    case Some(x) => x.U(DspReal.UnderlyingWidth.W)
    case _ => Output(UInt(DspReal.UnderlyingWidth.W))
  }

  private def oneOperandOperator(blackbox_gen: => BlackboxOneOperand) : DspReal = {
    val blackbox = blackbox_gen
    blackbox.io.in := node
    val out = Wire(new DspReal())
    out.node := blackbox.io.out
    out
  }

  private def twoOperandOperator(arg1: DspReal, blackbox_gen: => BlackboxTwoOperand) : DspReal = {
    val blackbox = blackbox_gen
    blackbox.io.in1 := node
    blackbox.io.in2 := arg1.node
    val out = Wire(new DspReal())
    out.node := blackbox.io.out
    out
  }

  private def twoOperandBool(arg1: DspReal, blackbox_gen: => BlackboxTwoOperandBool) : Bool = {
    val blackbox = blackbox_gen
    blackbox.io.in1 := node
    blackbox.io.in2 := arg1.node
    val out = Wire(Output(new Bool()))
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

  def ln (dummy: Int = 0): DspReal = {
    oneOperandOperator(Module(new BBFLn()))
  }

  def log10 (dummy: Int = 0): DspReal = {
    oneOperandOperator(Module(new BBFLog10()))
  }

  def exp (dummy: Int = 0): DspReal = {
    oneOperandOperator(Module(new BBFExp()))
  }

  def sqrt (dummy: Int = 0): DspReal = {
    oneOperandOperator(Module(new BBFSqrt()))
  }

  def pow (arg1: DspReal): DspReal = {
    twoOperandOperator(arg1, Module(new BBFPow()))
  }

  def floor (dummy: Int = 0): DspReal = {
    oneOperandOperator(Module(new BBFFloor()))
  }

  def ceil (dummy: Int = 0): DspReal = {
    oneOperandOperator(Module(new BBFCeil()))
  }

  def sin (dummy: Int = 0): DspReal = {
    oneOperandOperator(Module(new BBFSin()))
  }

  def cos (dummy: Int = 0): DspReal = {
    oneOperandOperator(Module(new BBFCos()))
  }

  def tan (dummy: Int = 0): DspReal = {
    oneOperandOperator(Module(new BBFTan()))
  }

  def asin (dummy: Int = 0): DspReal = {
    oneOperandOperator(Module(new BBFASin()))
  }

  def acos (dummy: Int = 0): DspReal = {
    oneOperandOperator(Module(new BBFACos()))
  }

  def atan (dummy: Int = 0): DspReal = {
    oneOperandOperator(Module(new BBFATan()))
  }

  def atan2 (arg1: DspReal): DspReal = {
    twoOperandOperator(arg1, Module(new BBFATan2()))
  }

  def hypot (arg1: DspReal): DspReal = {
    twoOperandOperator(arg1, Module(new BBFHypot()))
  }

  def sinh (dummy: Int = 0): DspReal = {
    oneOperandOperator(Module(new BBFSinh()))
  }

  def cosh (dummy: Int = 0): DspReal = {
    oneOperandOperator(Module(new BBFCosh()))
  }

  def tanh (dummy: Int = 0): DspReal = {
    oneOperandOperator(Module(new BBFTanh()))
  }

  def asinh (dummy: Int = 0): DspReal = {
    oneOperandOperator(Module(new BBFASinh()))
  }

  def acosh (dummy: Int = 0): DspReal = {
    oneOperandOperator(Module(new BBFACosh()))
  }

  def atanh (dummy: Int = 0): DspReal = {
    oneOperandOperator(Module(new BBFATanh()))
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
  // [stevo]: this doesn't work for UnderlyingWidth = 64...it produces 18446744073709552000 instead of 18446744073709551616
  // val bigInt2powUnderlying = BigInt(f"${math.pow(2.0, UnderlyingWidth)}%.0f")
  val bigInt2powUnderlying = BigInt(f"${math.pow(2.0, UnderlyingWidth/2)}%.0f")*BigInt(f"${math.pow(2.0, UnderlyingWidth/2)}%.0f")

  /** Creates a Real with a constant value.
    */
  def apply(value: Double): DspReal = {
    def longAsUnsignedBigInt(in: Long) = (BigInt(in >>> 1) << 1) + (in & 1)
    def doubleToBits(in: Double) = longAsUnsignedBigInt(java.lang.Double.doubleToRawLongBits(value))
    new DspReal(Some(doubleToBits(value)))
  }

  /**
    * Creates a Real by doing integer conversion from a (up to) DspReal.UnderlyingWidth-bit UInt.
    */
  def apply(value: UInt): DspReal = {
    val blackbox = Module(new BBFFromInt)
    blackbox.io.in := value
    val out = Wire(new DspReal())
    out.node := blackbox.io.out
    out
  }

  // just the typ
  def apply(): DspReal = new DspReal()
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
  implicit object DspRealRealImpl extends DspRealReal
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
trait DspRealIsReal extends Any with IsReal[DspReal] with DspRealOrder with DspRealSigned with hasContext {
  def toDouble(a: DspReal): DspReal = ???

  def ceil(a: DspReal): DspReal = ???
  def floor(a: DspReal): DspReal = ???
  def isWhole(a: DspReal): Bool = ???
  def round(a: DspReal): DspReal = ???
}

trait ConvertableToDspReal extends ConvertableTo[DspReal] with hasContext {
  def fromShort(n: Short): DspReal = DspReal(n.toInt)
  //def fromAlgebraic(n: Algebraic): DspReal = DspReal(n.toInt)
  def fromBigInt(n: BigInt): DspReal = DspReal(n.toInt)
  def fromByte(n: Byte): DspReal = DspReal(n.toInt)
  def fromDouble(n: Double): DspReal = DspReal(n)
  //def fromReal(n: Real): DspReal = DspReal(n.toDouble)
  //def fromRational(n: Rational): DspReal = DspReal(n.toDouble)
  def fromType[B](n: B)(implicit c: ConvertableFrom[B]): DspReal = DspReal(c.toDouble(n))
  def fromInt(n: Int): DspReal = DspReal(n)
  def fromFloat(n: Float): DspReal = DspReal(n.toDouble)
  def fromBigDecimal(n: BigDecimal): DspReal = DspReal(n.toDouble)
  def fromLong(n: Long): DspReal = DspReal(n)
}

trait DspRealReal extends DspRealRing with ConvertableToDspReal with DspRealIsReal with Real[DspReal] with hasContext {
  override def fromInt(n: Int): DspReal = super[DspRealRing].fromInt(n)
}
