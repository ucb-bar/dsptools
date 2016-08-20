// See LICENSE for license details.

package dsptools.numbers

import chisel3._


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

// TODO: parameterizable widths
class BBFFromInt extends BlackBox {
  val io = new Bundle() {
    val in = UInt(INPUT, DspReal.UnderlyingWidth)
    val out = UInt(OUTPUT, DspReal.UnderlyingWidth)
  }
}

// TODO: parameterizable widths
class BBFToInt extends BlackBox {
  val io = new Bundle() {
    val in = UInt(INPUT, DspReal.UnderlyingWidth)
    val out = UInt(OUTPUT, DspReal.UnderlyingWidth)
  }
}

class DspReal extends Bundle {
  val node = UInt(OUTPUT, DspReal.UnderlyingWidth)

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

  /** Returns this Real's value truncated to an integer, as a DspReal.UnderlyingWidth-bit UInt.
    * Behavior on overflow (possible with large exponent terms) is undefined.
    *
    * TODO: make underlying black box rtoi parameterized
    */
  def toUInt(dummy: Int = 0): UInt = {
//    val blackbox = Module(new BBFToInt)
//    blackbox.io.in := node
//    blackbox.io.out
    UInt(0, width=DspReal.UnderlyingWidth)
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

  /** Creates a Real by doing integer conversion from a (up to) DspReal.UnderlyingWidth-bit UInt.
    *
    * TODO: make underlying black box itor parameterized
    */
  def apply(value: UInt): DspReal = {
    val blackbox = Module(new BBFFromInt)
    blackbox.io.in := value
    val out = Wire(new DspReal)
    out.node := blackbox.io.out
    out
  }

}
