# Typeclasses supported by UInt, SInt, FixedPoint, DspReal, DspComplex[T]

* Eq Typeclass
  * a === b
  * a =/= b
* Ring Typeclass
  * a + b 
    * Affected by DspContext.overflowType, DspContext.numAddPipes
  * a - b 
    * Affected by DspContext.overflowType, DspContext.numAddPipes
  * -a 
    * Doesn't work for UInt
    * Affected by DspContext.overflowType (i.e. negating the most negative value for SInt, Fixedpoint causes overflow if you don't Grow), DspContext.numAddPipes
  * a * b 
    * Affected by DspContext.numMulPipes
    * For FixedPoint only: additionally affected by DspContext.trimType, DspContext.binaryPointGrowth (adds more fractional bits on top of the input amount)
      * If RoundhalfUp is selected for the trimType, be aware of the overflow behavior when you try to round to a # larger than what's supported by the input bitwidth! 
    * For Complex[T]: Additionally affected by DspContext.numAddPipes, DspConext.complexUse4Muls (true -> 4 real multiplies; false -> 3 real multiplies); the previous statement statement applies for Complex[T] if T is FixedPoint!
  * Ring[T].zero 
    * Zero literal of type T (or DspComplex[T])
  * Ring[T].one 
    * One literal of type T (or DspComplex[T])
* BinaryRepresentation Type
  * a >> n 
    * Arithmetic shift right where n is either Int or UInt 
    * Note: precision loss will result since the decimal point location is not adjusted
    * Doesn't work for DspComplex[T]
  * a << n 
    * Arithmetic shift left where n is either Int or UInt
    * Doesn't work for DspComplex[T]
  * a.signBit 
    * 0 if a is zero or positive; 1 if a is negative
    * Doesn't work for DspComplex[T]
  * a.div2(n) 
    * a/2^n
    * UInt: Consistent with a >> n (i.e. rounds 1/2 to 0)
    * SInt: Round output to nearest SInt via DspContext.trimType 
    * FixedPoint: Adjusts decimal point; up to you how much precision to keep via DspContext.trimType and DspContext.binaryPointGrowth (adds more fractional bits on top of the input amount)
    * For both FixedPoint and SInt, again, be aware of overflow behavior when RoundHalfUp is used for the most positive input
  * a.mul2(n) 
    * a*2^n
  * a.trimBinary(n)
    * Only affects FixedPoint; otherwise returns a
    * Trims to n fractional bits with rounding specified by DspContext.trimType. Be aware of overflow behavior!
    * DspComplex[T]: Trims both real and imaginary values to the same # of fractional bits (only for FixedPoint T)

# Typeclasses supported by UInt, SInt, FixedPoint, DspReal
* Order Type
  * a < b
  * a <= b
  * a > b
  * a >= b
  * a.min(b)
  * a.max(b)
* Signed Type
  * a.abs
    * Overflow behavior when a is the most negative value supported by width (For FixedPoint and SInt) is determined by DspContext.overflowType
  * a.isSignZero
  * a.isSignPositive
  * a.isSignNegative
  * a.isSignNonZero
  * a.isSignNonPositive
  * a.isSignNonNegative
* IsReal Type
  * a.ceil
    * Overflow for taking a ceil of the most positive # (for FixedPoint) behavior specify via DspContext.overflowType
  * a.floor
  * a.round 
    * Round half up to positive infinity (biased!)
    * For FixedPoint, overflow for taking a ceil of the most positive # (for FixedPoint) behavior specify via DspContext.overflowType
  * a.truncate
  * a.isWhole

# Typeclasses supported by UInt, SInt
* IsIntegral
  * a % b
  * a.isOdd
  * a.isEven

# Operations (non-synthesizable) supported by DspReal (not part of a type class)
* a / b
* Requires `import dsptools.numbers.RealTrig`
  * Implemented with Verilator default operations
    * ln(a)
    * log10(a)
    * exp(a)
    * sqrt(a)
    * pow(a, n)
      * a^n
  * Implemented with series approximations, etc. (Verilator doesn't support these SystemVerilog ops -- no guarantees on precision! Allowable input/output ranges are determined by the particular function performed!)
    * sin(a)
    * cos(a)
    * tan(a)
    * atan(a)
    * asin(a)
    * acos(a)
    * atan2(b, a)
      * Principal value of the arg of a+bi; output range (-pi,pi]
    * hypot(a, b)
      * sqrt(a^2 + b^2)
    * sinh(a)
    * cosh(a)
    * tanh(a)
    * asinh(a)
    * acosh(a)
    * atanh(a)

# Operations for DspComplex[T] where T <: Data : Ring
  * DspComplex.j[T]
    * Creates a DspComplex literal j where the real and imaginary parts are of type T
  * a.mulj()
    * Multiplies the complex value a by j
  * a.divj()
    * Divides the complex value a by j
  * a.conj()
    * Returns the complex conjugate of a
  * a.abssq()
    * Returns the squared norm of a (If a = x + y * i, returns x^2 + y^2)
    * Context behavior goverened by the context behavior of add and multiply operations above (See Ring)

# Operations supported UInt, SInt, FixedPoint, DspReal for type conversion [ChiselConvertableFrom type class]
  * a.intPart()
    * For a = UInts and SInts, just return the number represented as an SInt (with sign bit of 0 for UInt)
    * For a = FixedPoint, DspReal, returns the integer part (truncated) as an SInt
  * a.asFixed()
    * For UInts and SInts, just returns the number represented as a (signed) FixedPoint with 0 fractional bits
    * For FixedPoint, returns itself
    * Doesn't work for DspReal -- must supply a prototype with explicit binaryPoint
  * a.asFixed(b)
    * For UInts an SInts, ignores b and just does as above
    * For FixedPoint, ignores b and returns itself
    * For DspReal, b is a FixedPoint prototype i.e. this will create a FixedPoint approximation of the real value to the # of fractional bits specified by b's binaryPoint. The LSB is chosen via round half up on the RHS bits that were removed.
  * a.asReal()
    * For UInts, SInts, and FixedPoint, converts them into the equivalent Verilog real (DspReal)
    * If a is a DspReal, returns itself
  * Note that these are somewhat dangerous functions intended to be used so that you can do system model verification with "ideal" = DspReal signals and then swap out blocks individually for FixedPoint versions to look at quantization effects of individual blocks on system performance. You need this to connect between FixedPoint and DspReal IOs (only use these for between module connections!). **Caution! DspReal nodes are still unsynthesizable!**
  * Note also that these functions are very different from something like *a.asUInt*, which simply does a reinterpret cast on raw bits, ignoring binary point, etc. In that case, the result might make no numerical sense.

# Creating Literals 
* Chisel
  * -150.S 
    * -150 is Scala Int converted to a Chisel SInt
    * Chisel determines width required
  * -150.S(10.W)
    * Allocates 10 bits to represent -150 (important for making Vecs of Lits, where the Lits have to all have the same width!)
  * 150.U
    * Scala Int of 150 converted to a Chisel UInt
  * 150.U(10.W)
  * true.B
    * Scala boolean converted to Chisel true Bool
  * -3.14.F(10)
    * Scala Double -3.14 converted to FixedPoint with 10 fractional bits (and Chisel-determined width)
    * Note: BP = 10, but it's not a BinaryPoint type (It's an Int!)
  * FixedPoint.fromDouble(-3.14, width = 20, binaryPoint = 10)
    * If binaryPoint is *not* specified, 0 fractional bits are assumed (Scala rounded); otherwise, it reflects the # of fractional bits allocated (Scala rounded)
    * If width is *not* specified, Chisel tries to infer the needed width (width = # fractional bits + # integer bits + 1 for sign)
    * Again, note that width and binaryPoint are Int's and not Chisel Width's or Chisel BinaryPoint's
  * Vec(lutVals map (x => x.S(10.W)))
    * lutVals is a sequence of Ints and the map converts it to a Seq[SInt] with width 10
    * Vec Lits *must* have the same width
    * You can also make a Vec of DspComplex's
* ChiselDSP-Specific
  * DspReal(-3.14)
    * Generates a non-synthesizable SystemVerilog Real Lit from a Scala Double. 
  * DspComplex(-3.14.F(10), 3.14.F(10))
    * Creates a DspComplex literal with real = -3.14 and imaginary = 3.14
    * Real and imaginary parts should be the same type [T <: Data:Ring] (likely UInt, SInt, FixedPoint, or DspReal, as used above)
  * DspComplex[T](Complex(-3.3, 3.3))
    * Requires the use of `import breeze.math.Complex` 
    * Results in a DspComplex literal with real = -3.3, imag = 3.3
    * Binary point precision (fractional width) is set via DspContext.binaryPoint
    * [T <: Data:Ring:ConvertableTo], so you can use UInt, SInt, FixedPoint, DspReal
  * DspComplex.proto(Complex(-3.3, 3.3), gen)
    * gen must be [T <: Data:Ring:ConvertableTo]
    * gen specifies the type of Lit (UInt, SInt, FixedPoint, DspReal), and for FixedPoint, determines the binaryPoint
  * DspComplex.protoWithFixedWidth(Complex(-3.3, 3.3), gen)
    * Same as above, but gen is also used to set the width
* With Typeclasses [T <: Data:ConvertableTo] (UInt, SInt, FixedPoint, DspReal)
  * Using prototypes proto of type T
    * proto.fromDouble(3.14)
      * For SInts and UInts, the -3.14 double is rounded (an error will be thrown if you create a negative UInt Lit)
      * For FixedPoint, the binaryPoint (must be defined!) of proto will be used
    * proto.fromDoubleWithFixedWidth(3.14)
      * Same as above, but the Lit will also have proto's width (must be defined!)
    * ConvertableTo[T].fromDouble(3.14)
      * The Scala Double 3.14 will be converted to a Lit of type T
      * Width is automatically determined
      * For FixedPoint: # Fractional bits set via DspContext.binaryPoint
      * For SInt, UInt: Rounds
 
# Type Declarations (T)
* Bool()
* UInt(16.W) -- 16-bit UInt
* UInt() -- width inferred
* SInt(16.W) -- 16-bit SInt
* SInt() -- width inferred
* FixedPoint(16.W, 8.BP) -- 16-bit FixedPoint with 8 fractional bits
* DspReal()
* DspComplex(gen) 
  * gen has [T <: Data:RealBits]
  * real and imag parts have type T given by gen (also have gen's width/binary point if provided)
* Vec(n, gen)
  * gen is of type [T <: Data]
  * Elements have the same width as gen
* Inside an IO Bundle, you should wrap these declarations (or at some higher Aggregate level) as Input or Output i.e. Input(Bool()) or Output(Bool())
* If you're trying to make a wire of type T that you later assign to, you must use something like Wire(Bool()) or Wire(Vec(n, gen))

---

# Documentation TODO (not DSP specific)
* AsUInt
* ROM
* RegNext, RegEnable, etc.
* Passing custom clk, reset
* How to use ranges
* [Mux, Mux1H](https://github.com/ucb-bar/chisel3/wiki/Muxes%20and%20Input%20Selection) 
* Cat
* [Bitwise ops](https://github.com/ucb-bar/chisel3/wiki/Builtin%20Operators) (&, |, ^, etc.) 
* := , <>
* [Black box](https://github.com/ucb-bar/chisel3/wiki/BlackBoxes)
* [Annotation](https://github.com/ucb-bar/chisel3/wiki/Annotations%20Extending%20Chisel%20and%20Firrtl)
* [Mem, SeqMem](https://github.com/ucb-bar/chisel3/wiki/Memories)
* When
* [Enum](https://github.com/ucb-bar/chisel3/wiki/Cookbook)
* ValidIO, DecoupledIO

