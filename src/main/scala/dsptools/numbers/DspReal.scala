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
    val out = Wire(DspReal())
    out.node := blackbox.io.out
    out
  }

  private def twoOperandOperator(arg1: DspReal, blackbox_gen: => BlackboxTwoOperand) : DspReal = {
    val blackbox = blackbox_gen
    blackbox.io.in1 := node
    blackbox.io.in2 := arg1.node
    val out = Wire(DspReal())
    out.node := blackbox.io.out
    out
  }

  private def twoOperandBool(arg1: DspReal, blackbox_gen: => BlackboxTwoOperandBool) : Bool = {
    val blackbox = blackbox_gen
    blackbox.io.in1 := node
    blackbox.io.in2 := arg1.node
    val out = Wire(Output(Bool()))
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

  // Assumes you're using chisel testers
  private def backendIsVerilator: Boolean = {
    chisel3.iotesters.Driver.optionsManager.testerOptions.backendName == "verilator"  
  }

  // The following are currently not supported with Verilator, so they've been implemented through other means
  // with approximately 6 decimal points of precision

  private def pi = DspReal(math.Pi)
  private def twoPi = DspReal(2 * math.Pi)
  private def halfPi = DspReal(math.Pi / 2)
  private def negPi = DspReal(-math.Pi)
  private def negHalfPi = DspReal(-math.Pi / 2)
  private def zero = DspReal(0.0)
  private def one = DspReal(1.0)

  // TODO: Check out http://www.netlib.org/fdlibm/k_sin.c
  // Swept in increments of 0.0001pi, and got ~11 decimal digits of accuracy
  def sin (dummy: Int = 0): DspReal = {
    if (backendIsVerilator) {
      // Taylor series; Works best close to 0 (-pi/2, pi/2) -- so normalize! 
      def sinPiOver2(in: DspReal): DspReal = {
        val nmax = TrigUtility.numTaylorTerms - 1
        //val xpow = (0 to nmax).map(n => in.pow(DspReal(2 * n + 1)))
        // Multiply by extra x later improves accuracy
        val xpow = (0 to nmax).map(n => in.pow(DspReal(2 * n)))
        // Break coefficient into two step process b/c of precision limitations
        val terms = TrigUtility.sinCoeff(nmax).zip(xpow) map { case ((c, scale), x) => DspReal(c) * x / DspReal(scale)}
        terms.reduceRight(_ + _) * in
      }
      import dsptools.numbers.implicits._
      val num2Pi = (this / twoPi).truncate()
      // Repeats every 2*pi, so normalize to -pi, pi
      val normalized2Pi= this - num2Pi * twoPi
      val temp1 = Mux(normalized2Pi > pi, normalized2Pi - twoPi, normalized2Pi)
      val normalizedPi = Mux(normalized2Pi < negPi, normalized2Pi + twoPi, temp1)
      val q2 = normalizedPi > halfPi
      val q3 = normalizedPi < negHalfPi
      // sin(x + pi) = -sin(x)
      // sin(pi - x) = sin(x)
      val temp2 = Mux(q2, pi - normalizedPi, normalizedPi)
      val normalizedHalfPi= Mux(q3, pi + normalizedPi, temp2)

      // Half angle -> sin(x/2) = (-1)^(floor(x/(2pi)) * sqrt((1-cosx)/2))
      // x negative -> sin(x/2) = -1 * sqrt((1-cosx)/2))
      // x positive -> sin(x/2) = sqrt((1-cosx)/2))
      // 2*sin^2(x/2) = 1-cosx
      // cosx = 1 - 2*sin^2(x/2)
      // Use for x > pi/4
      // sinx = cos(x - pi/2) = 1 - 2 * sin^2(x/2 - pi/4)
      // Use for x < -pi/4
      // sinx = -cos(x + pi/2) = -1 * [1 - 2*sin^2(x/2 + pi/4)]
      // Closer to 0 -> more accurate!
      val sinPiOver4Out = sinPiOver2((normalizedHalfPi - halfPi) / DspReal(2.0))
      val sinNegPiOver4Out = sinPiOver2((normalizedHalfPi + halfPi) / DspReal(2.0))
      val sinPiOver2Out = sinPiOver2(normalizedHalfPi)

      val outTemp1 = Mux( normalizedHalfPi > pi/DspReal(4), 
                          one - DspReal(2) * sinPiOver4Out * sinPiOver4Out, 
                          sinPiOver2Out)
      val outTemp2 = Mux(normalizedHalfPi < pi/DspReal(-4), 
                        DspReal(-1) * (one - DspReal(2) * sinNegPiOver4Out * sinNegPiOver4Out),
                        outTemp1)
      Mux(q3, zero - outTemp2, outTemp2)
    }
    else oneOperandOperator(Module(new BBFSin()))
  }

  def cos (dummy: Int = 0): DspReal = {
    if (backendIsVerilator) (this + halfPi).sin()
    else oneOperandOperator(Module(new BBFCos()))
  }







  // 5 dec bots
  def tan (dummy: Int = 0): DspReal = {
    if (backendIsVerilator) {
      // Taylor series; Works best close to 0 (-pi/2, pi/2) -- so normalize! 
      def tanPiOver2(in: DspReal): DspReal = {
        val nmax = TrigUtility.numTaylorTerms
        val xpow = (1 to nmax).map(n => in.pow(DspReal(2 * n - 1)))
        val terms = TrigUtility.tanCoeff(nmax).zip(xpow) map { case (c, x) => DspReal(c) * x }
        terms.reduceRight(_ + _)
      }
      import dsptools.numbers.implicits._
      val numPi = (this / pi).truncate()
      // Repeats every pi, so normalize to -pi/2, pi/2
      // tan(x + pi) = tan(x)
      val normalizedPi= this - numPi * pi
      val temp1 = Mux(normalizedPi > halfPi, normalizedPi - pi, normalizedPi)
      val normalizedHalfPi = Mux(normalizedPi < negHalfPi, normalizedPi + pi, temp1)
      tanPiOver2(normalizedHalfPi)
//tan blows up tooquickly
      this.sin()/this.cos()

    }
    else oneOperandOperator(Module(new BBFTan()))
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
    def doubleToBigInt(in: Double): BigInt = longAsUnsignedBigInt(java.lang.Double.doubleToRawLongBits(in))
    new DspReal(Some(doubleToBigInt(value)))
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
    val out = Wire(DspReal())
    out.node := blackbox.io.out
    out
  }

  def apply(): DspReal = new DspReal()

}