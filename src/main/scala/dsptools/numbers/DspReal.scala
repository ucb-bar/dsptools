// See LICENSE for license details.

package dsptools.numbers

import chisel3._

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

class BBFAdd extends BlackboxTwoOperand {
  setInline("BlackBoxAdd.v", """
module BBFAdd(
    input  [63:0] in1,
    input  [63:0] in2,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($bitstoreal(in1) + $bitstoreal(in2));
  end
endmodule
  """)
}

class BBFSubtract extends BlackboxTwoOperand {
  setInline("BlackBoxSubtract.v", """
module BBFSubtract(
    input  [63:0] in1,
    input  [63:0] in2,
    output [63:0] out
);
  always @* begin
  out <= $realtobits($bitstoreal(in1) - $bitstoreal(in2));
  end
endmodule
  """)
}

class BBFMultiply extends BlackboxTwoOperand {
  setInline("BlackBoxMultiply.v", """
module BBFMultiply(
    input  [63:0] in1,
    input  [63:0] in2,
    output [63:0] out
);
  always @* begin
  out <= $realtobits($bitstoreal(in1) * $bitstoreal(in2));
  end
endmodule
  """)
}

class BBFDivide extends BlackboxTwoOperand {
  setInline("BlackBoxDivide.v", """
module BBFDivide(
    input  [63:0] in1,
    input  [63:0] in2,
    output [63:0] out
);
  always @* begin
  out <= $realtobits($bitstoreal(in1) / $bitstoreal(in2));
  end
endmodule
  """)
}

class BBFGreaterThan extends BlackboxTwoOperandBool {
  setInline("BBFGreaterThan.v", """
module BBFGreaterThan(
    input  [63:0] in1,
    input  [63:0] in2,
    output out
);
  always @* begin
  out <= $bitstoreal(in1) > $bitstoreal(in2);
  end
endmodule
  """)
}

class BBFGreaterThanEquals extends BlackboxTwoOperandBool {
  setInline("BBFGreaterThanEquals.v", """
module BBFGreaterThanEquals(
    input  [63:0] in1,
    input  [63:0] in2,
    output out
);
  always @* begin
  out <= $bitstoreal(in1) >= $bitstoreal(in2);
  end
endmodule
  """)
}

class BBFLessThan extends BlackboxTwoOperandBool {
  setInline("BBFLessThan.v", """
module BBFLessThan(
    input  [63:0] in1,
    input  [63:0] in2,
    output out
);
  always @* begin
  out <= $bitstoreal(in1) < $bitstoreal(in2);
  end
endmodule
  """)
}

class BBFLessThanEquals extends BlackboxTwoOperandBool {
  setInline("BBFLessThanEquals.v", """
module BBFLessThanEquals(
    input  [63:0] in1,
    input  [63:0] in2,
    output out
);
  always @* begin
  out <= $bitstoreal(in1) <= $bitstoreal(in2);
  end
endmodule
  """)
}

class BBFEquals extends BlackboxTwoOperandBool {
  setInline("BBFEquals.v", """
module BBFEquals(
    input  [63:0] in1,
    input  [63:0] in2,
    output out
);
  always @* begin
  out <= $bitstoreal(in1) == $bitstoreal(in2);
  end
endmodule
  """)
}

class BBFNotEquals extends BlackboxTwoOperandBool {
  setInline("BBFNotEquals.v", """
module BBFNotEquals(
    input  [63:0] in1,
    input  [63:0] in2,
    output out
);
  always @* begin
  out <= $bitstoreal(in1) != $bitstoreal(in2);
  end
endmodule
  """)
}

/** Math operations from IEEE.1364-2005 **/
class BBFLn extends BlackboxOneOperand {
  setInline("BBFLn.v", """
module BBFLn(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($ln($bitstoreal(in)));
  end
endmodule
  """)
}

class BBFLog10 extends BlackboxOneOperand {
  setInline("BBFLog10.v", """
module BBFLog10(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($log10($bitstoreal(in)));
  end
endmodule
  """)
}

class BBFExp extends BlackboxOneOperand {
  setInline("BBFExp.v", """
module BBFExp(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($exp($bitstoreal(in)));
  end
endmodule
  """)
}

class BBFSqrt extends BlackboxOneOperand {
  setInline("BBFSqrt.v", """
module BBFSqrt(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($sqrt($bitstoreal(in)));
  end
endmodule
  """)
}

class BBFPow extends BlackboxTwoOperand {
  setInline("BBFPow.v", """
module BBFPow(
    input  [63:0] in1,
    input  [63:0] in2,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($pow($bitstoreal(in1), $bitstoreal(in2)));
  end
endmodule
  """)
}

class BBFFloor extends BlackboxOneOperand {
  setInline("BBFFloor.v", """
module BBFFloor(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($floor($bitstoreal(in)));
  end
endmodule
  """)
}

class BBFCeil extends BlackboxOneOperand {
  setInline("BBFCeil.v", """
module BBFCeil(
    input  [63:0] in,
    output reg [63:0] out
);
  always @* begin
  out <= $realtobits($ceil($bitstoreal(in)));
  end
endmodule
  """)
}

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

