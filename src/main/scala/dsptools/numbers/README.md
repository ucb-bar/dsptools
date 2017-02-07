> Note: Type classes require that you `import dsptools.numbers.implicits._`
> To support the operations below for DspReal, FixedPoint, SInt, UInt, you want a: T where `[T <: Data:RealBits]`
> If you want to support IsIntegral operations for SInt + UInt (in addition to the others), you want a: T where `[T <: Data:IntegerBits]`
> DspReal is *not* synthesizable!

# DspContext
* DspContext allows you to change how certain operations behave via a dynamic variable
* The DspContext case class contains the following fields:
  * overflowType (type: OverflowType; default: Grow) specifies overflow behavior for ops like a + b, a - b, -a
    * Saturate (not implemented)
    * Wrap: Wrap output on overflow (output has max width of either input)
    * Grow: Grow bits so your output is numerically correct
  * trimType (type: TrimType; default: Floor) specifies how Fixed point ops like a * b, a.trimBinary(n), and a.div2(n) should round results
    * NoTrim: Keep maximal precision + bit growth
    * Truncate (not implemented)
    * RoundHalfUp: Assumes binaryPoints are well defined, 
      * For *, div2: Rounds half up to a.binaryPoint.get.max(b.binaryPoint.get) + DspContext.binaryPointGrowth # of fractional bits -- looks at the result's next bit
      * For trimBinary: Rounds half up to n fractional bits
    * Floor: Rounds to negative infinity; # of fractional bits is the same as in RoundHalfUp case
    * Caution: Any time a known binary point is expected, you might run into Chisel/Firrtl bugs. Please let us know if you suspect something is wrong.
  * binaryPointGrowth (type: Int; default: 1)
    * Use case explained above
    * Requires that the input binary point is well defined
  * binaryPoint (type: Option[Int]; default: Some(14))
    * Specifies the default # of fractional bits when creating FixedPoint literals with something like ConvertableTo[T].fromDouble(3.14)
  * numBits (type: Option[Int]; default: Some(16), unused)
  * complexUse4Muls (type: Boolean, default: false)
    * true: Use 4 real multiplies to perform a complex multiply
    * false: Use 3 real multiplies to perform a complex multiply
  * numMulPipes (type: Int; default: 0)
    * # of pipeline registers to add after a multiply operation between two inputs of type [T <: Data:RealBits]
    * Note: This only applies to multiplications with [T <: Data:RealBits]; DspComplex multiplication utilizes some combination of this and numAddPipes
  * numAddPipes (type: Int; default: 0)
    * # of pipeline registers to add after an add operation between two inputs of type [T <: Data:RealBits]
* How to Use
  * You must have `import dsptools._`
  * You can change the DspContext @ any level (Top, Module, local operations)
  * Changing the local +, - overflow behavior (while keeping other options the same; only for the operations inside the braces -- otherwise use defaults)
    ```
    val sum = DspContext.withOverflowType(Wrap) { a + b }
    ```
  * `val prod = DspContext.withTrimType(RoundHalfUp) { a * b }`
  * `val prod = DspContext.withBinaryPointGrowth(3) { a * b }`
  * `val lit = DspContext.withBinaryPoint(8) { ConvertableTo[FixedPoint].fromDouble(3.14) }`
  * `val prod = DspContext.withComplexUse4Muls(true) { ca * cb }`
  * `val prod = DspContext.withNumMulPipes(2) { a * b }`
  * `val sum = DspContext.withNumAddPipes(1) { a + b }`
  * Change several options locally:
    ```
    val prod = DspContext.alter(DspContext.current.copy(trimType = NoTrim, binaryPointGrowth = 3, numMulPipes = 2)) { a * b }
    ```
  * Figure out how many pipeline registers are used in a Complex multiply:
    * `DspContext.current.complexMulDly`

# Operations supported by T of UInt, SInt, FixedPoint, DspReal (or DspComplex[T])
* Eq Type 
  * a === b
  * a =/= b
* Ring Type
  * a + b 
    * Affected by DspContext.overflowType, DspContext.numAddPipes
  * a - b 
    * Affected by DspContext.overflowType, DspContext.numAddPipes
  * -a 
    * Doesn't work for UInt
    * Affected by DspContext.overflowType, DspContext.numAddPipes
  * a * b 
    * Affected by DspContext.numMulPipes
    * For FixedPoint only: additionally affected by DspContext.trimType, DspContext.binaryPointGrowth (adds more fractional bits on top of the input amount)
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
    * 1 if a is zero or positive; 0 if a is negative
    * Doesn't work for DspComplex[T]
  * a.div2(n) 
    * a/2^n
    * UInt: Consistent with a >> n (i.e. rounds 1/2 to 0)
    * SInt: Round output to nearest SInt via DspContext.trimType
    * FixedPoint: Adjusts decimal point; up to you how much precision to keep via DspContext.trimType and DspContext.binaryPointGrowth (adds more fractional bits on top of the input amount)
  * a.mul2(n) 
    * a*2^n
  * a.trimBinary(n)
    * Only affects FixedPoint; otherwise returns a
    * Trims to n fractional bits with rounding specified by DspContext.trimType
    * DspComplex[T]: Trims both real and imaginary values to the same # of fractional bits (only for FixedPoint T)

# Additional operations supported by T of UInt, SInt, FixedPoint, DspReal
* Order Type
  * a < b
  * a <= b
  * a > b
  * a >= b
  * a.min(b)
  * a.max(b)
* Signed Type
  * a.abs
  * a.isSignZero
  * a.isSignPositive
  * a.isSignNegative
  * a.isSignNonZero
  * a.isSignNonPositive
  * a.isSignNonNegative
* IsReal Type
  * a.ceil
  * a.floor
  * a.round 
    * Round half up to positive infinity (biased!)
  * a.truncate
  * a.isWhole

# Additional operations support by T of UInt, SInt
* IsIntegral
  * a % b
  * a.isOdd
  * a.isEven

# Additional (non-synthesizable) operations for DspReal (Not part of type classes)
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

# Additional operations for Complex[T] where T is UInt, SInt, FixedPoint, or DspReal
  * DspComplex.j[T]
    * Creates a DspComplex literal j where the real and imaginary parts are of type T
  * a.j
    * Multiplies the complex value a by j
  * a.divj
    * Divides the complex value a by j
  * a.conj
    * Returns the complex conjugate of a
  * a.abssq
    * Returns the squared norm of a (If a = x + y * i, returns x^2 + y^2)

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













TODO:
Reg, 
Clk
ranging
Mux
gen module -- parameterized
Bundle (need clone type)
override reset, clk
* asuint, asfixedpoint, etc.
ConvertableFrom: intPart, asFixed, asReal
Tester options: peek, poke, expect, how to change tolerance, etc.
other options
