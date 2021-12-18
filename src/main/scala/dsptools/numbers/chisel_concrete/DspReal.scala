// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3._
import chisel3.util.Mux1H
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor

//scalastyle:off number.of.methods
class DspReal() extends Bundle {

  val node: UInt = Output(UInt(DspReal.underlyingWidth.W))

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

  def round(): DspReal = (this + DspReal(0.5)).floor()

  def truncate(): DspReal = {
    Mux(this < DspReal(0.0), this.ceil(), this.floor())
  }

  def abs(): DspReal = Mux(this < DspReal(0.0), DspReal(0.0) - this, this)

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
  // Can add more half angle recursion for more precision
  //scalastyle:off magic.number
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
      // Closer to 0 -> more accurate! [can theoretically recurse with more half angle to get better precision]
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
    else {
      oneOperandOperator(Module(new BBFSin()))
    }
  }

  def cos (dummy: Int = 0): DspReal = {
    if (backendIsVerilator) (this + halfPi).sin()
    else oneOperandOperator(Module(new BBFCos()))
  }

  // Swept input at 0.0001pi increments. For tan < 1e9, ~8 decimal digit precision (fractional)
  // WARNING: tan blows up (more likely to be wrong when abs is close to pi/2)
  def tan (dummy: Int = 0): DspReal = {
    if (backendIsVerilator) {
      def tanPiOver2(in: DspReal): DspReal = {
        in.sin()/in.cos()
      }
      val numPi = (this / pi).truncate()
      // Repeats every pi, so normalize to -pi/2, pi/2
      // tan(x + pi) = tan(x)
      val normalizedPi= this - numPi * pi
      val temp = Mux(normalizedPi > halfPi, normalizedPi - pi, normalizedPi)
      val normalizedHalfPi = Mux(normalizedPi < negHalfPi, normalizedPi + pi, temp)
      
      // Also note: tan(x) = 2*tan(x/2)/(1-tan^2(x/2))
      tanPiOver2(normalizedHalfPi)
    }
    else oneOperandOperator(Module(new BBFTan()))
  }

  // Correct to 9 decimal digits sweeping by 0.0001pi
  // See http://myweb.lmu.edu/hmedina/papers/reprintmonthly156-161-medina.pdf
  def atan (dummy: Int = 0): DspReal = {
    if (backendIsVerilator) {
      def arctanPiOver2(in: DspReal): DspReal = {
        val m = TrigUtility.atanM
        //val xpow1 = (1 to (2 * m)).map(j => in.pow(DspReal(2 * j - 1)))
        //val xpow2 = (0 to (4 * m - 2)).map(j => in.pow(DspReal(4 * m + j + 1)))
        // Move single multiply by x until later
        val xpow1 = (1 to (2 * m)).map(j => in.pow(DspReal(2 * j - 2)))
        val xpow2 = (0 to (4 * m - 2)).map(j => in.pow(DspReal(4 * m + j)))
        val terms1 = TrigUtility.atanCoeff1(m).zip(xpow1) map { case (c, x) => DspReal(c) * x }
        val terms2 = TrigUtility.atanCoeff2(m).zip(xpow2) map { case (c, x) => DspReal(c) * x }
        (terms1 ++ terms2).reduceRight(_ + _) * in
      }
      val isNeg = this.signBit()
      // arctan(-x) = -arctan(x)
      val inTemp = this.abs()
      // arctan(x) = pi/2 - arctan(1/x) for x > 0
      // Approximation accuracy in [0, 1]
      val outTemp = Mux(inTemp > one, halfPi - arctanPiOver2(one / inTemp), arctanPiOver2(inTemp))
      Mux(isNeg, zero - outTemp, outTemp)
    }
    else oneOperandOperator(Module(new BBFATan()))
  }

  // See https://en.wikipedia.org/wiki/Inverse_trigonometric_functions

  // Must be -1 <= x <= 1
  def asin (dummy: Int = 0): DspReal = {
    if (backendIsVerilator) {
      val sqrtIn = one - (this * this)
      val atanIn = this / (one + sqrtIn.sqrt())
      DspReal(2) * atanIn.atan()
    }
    else oneOperandOperator(Module(new BBFASin()))
  }

  // Must be -1 <= x <= 1
  def acos (dummy: Int = 0): DspReal = {
    if (backendIsVerilator) {
      halfPi - this.asin()
    }
    else oneOperandOperator(Module(new BBFACos()))
  }

  // Output in the range (-pi, pi]
  // y.atan2(x)
  def atan2 (arg1: DspReal): DspReal = {
    if (backendIsVerilator) {
      val x = arg1
      val y = this
      val atanArg = y / x
      val atanRes = atanArg.atan()
      val muxIn: Iterable[(Bool, DspReal)] = Iterable(
        (x > zero) -> atanRes, 
        (x.signBit() && !y.signBit()) -> (atanRes + pi), 
        (x.signBit() && y.signBit()) -> (atanRes - pi), 
        (x === zero && y > zero) -> halfPi, 
        (x === zero && y.signBit()) -> negHalfPi, 
        (x === zero && y === zero) -> atanArg               // undefined
      )
      Mux1H(muxIn)
    }
    else twoOperandOperator(arg1, Module(new BBFATan2()))
  }

  def hypot (arg1: DspReal): DspReal = {
    if (backendIsVerilator) (this * this + arg1 * arg1).sqrt()
    else twoOperandOperator(arg1, Module(new BBFHypot()))
  }

  // See https://en.wikipedia.org/wiki/Hyperbolic_function

  def sinh (dummy: Int = 0): DspReal = {
    if (backendIsVerilator) (this.exp() - (zero - this).exp()) / DspReal(2)
    else oneOperandOperator(Module(new BBFSinh()))
  }

  def cosh (dummy: Int = 0): DspReal = {
    if (backendIsVerilator) (this.exp() + (zero - this).exp()) / DspReal(2)
    else oneOperandOperator(Module(new BBFCosh()))
  }

  def tanh (dummy: Int = 0): DspReal = {
    if (backendIsVerilator) (this.exp() - (zero - this).exp()) / (this.exp() + (zero - this).exp())
    else oneOperandOperator(Module(new BBFTanh()))
  }

  // Requires Breeze for testing:

  def asinh (dummy: Int = 0): DspReal = {
    if (backendIsVerilator) ((this * this + one).sqrt() + this).ln()
    else oneOperandOperator(Module(new BBFASinh()))
  }

  // x >= 1
  def acosh (dummy: Int = 0): DspReal = {
    if (backendIsVerilator) ((this * this - one).sqrt() + this).ln()
    else oneOperandOperator(Module(new BBFACosh()))
  }

  // |x| < 1
  def atanh (dummy: Int = 0): DspReal = {
    if (backendIsVerilator) ((one + this) / (one - this)).ln() / DspReal(2)
    else oneOperandOperator(Module(new BBFATanh()))
  }
  
  // Not used directly -- there's an equivalent in the type classes (was causing some confusion)
  /*
  def intPart(dummy: Int = 0): DspReal = {
    oneOperandOperator(Module(new BBFIntPart()))
  }
  */

  /** Returns this Real's value rounded to a signed integer.
    * Behavior on overflow (possible with large exponent terms) is undefined.
    */
  def toSInt(dummy: Int = 0): SInt = {
    println(Console.YELLOW + "WARNING: Real -> SInt === THIS DESIGN IS NOT SYNTHESIZABLE!" + Console.RESET)
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

  @deprecated("addZero doesn't do anything", "dsptools 1.5")
  def apply(value: Double, addZero: Boolean): DspReal = apply(value)

  /** Creates a Real with a constant value.
    */
  def apply(value: Double): DspReal = {
    // See http://stackoverflow.com/questions/21212993/unsigned-variables-in-scala
    def longAsUnsignedBigInt(in: Long): BigInt = (BigInt(in >>> 1) << 1) + (in & 1)
    def doubleToBigInt(in: Double): BigInt = longAsUnsignedBigInt(java.lang.Double.doubleToRawLongBits(in))
    (new DspReal()).Lit(_.node -> doubleToBigInt(value).U)
  }

  /**
    * Creates a Real from an SInt
    */
  def apply(value: SInt): DspReal = {
    println(Console.YELLOW + "WARNING: SInt -> Real === THIS DESIGN IS NOT SYNTHESIZABLE!" + Console.RESET)
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
