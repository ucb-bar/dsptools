package dsptools.numbers
import chisel3._

class DspReal(lit: Option[BigInt] = None) extends Bundle {
  
  val node: UInt = lit match {
    case Some(x) => x.U(DspReal.underlyingWidth.W)
    case _ => Output(UInt(DspReal.underlyingWidth.W))
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

  // The following are currently not supported with Verilator
  /*
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
  */

  // Not use directly -- there's an equivalent in the type classes (was causing some confusion)
  /*
  def intPart(dummy: Int = 0): DspReal = {
    oneOperandOperator(Module(new BBFIntPart()))
  }
  */

  /** Returns this Real's value rounded to a signed integer.
    * Behavior on overflow (possible with large exponent terms) is undefined.
    */
  def toSInt(dummy: Int = 0): SInt = {
    val blackbox = Module(new BBFToInt)
    blackbox.io.in := node
    // BB output always UInt -- need to cast
    blackbox.io.out.asSInt
  }

  /** Returns this Real's value as its bit representation in DspReal.underlyingWidth-bit floating point.
    */
  def toDoubleBits(dummy: Int = 0): UInt = {
    node
  }
}

object DspReal {
  val underlyingWidth = 64
  // Need to separate out, otherwise answer is incorrect (?)
  def width2NextBigInt = BigInt(f"${math.pow(2.0, underlyingWidth/2)}%.0f") * BigInt(f"${math.pow(2.0, underlyingWidth/2)}%.0f")

  /** Creates a Real with a constant value.
    */
  def apply(value: Double): DspReal = {
    // See http://stackoverflow.com/questions/21212993/unsigned-variables-in-scala
    def longAsUnsignedBigInt(in: Long): BigInt = (BigInt(in >>> 1) << 1) + (in & 1)
    def doubleToBits(in: Double): BigInt = longAsUnsignedBigInt(java.lang.Double.doubleToRawLongBits(in))
    new DspReal(Some(doubleToBits(value)))
  }

  /**
    * Creates a Real from an SInt
    */
  def apply(value: SInt): DspReal = {
    val blackbox = Module(new BBFFromInt)
    val extendedSInt = Wire(SInt(underlyingWidth.W))
    extendedSInt := value
    // Black box expects 64-bit UInt input
    blackbox.io.in := extendedSInt.asUInt
    val out = Wire(new DspReal())
    out.node := blackbox.io.out
    out
  }

  def apply(): DspReal = new DspReal()

}

