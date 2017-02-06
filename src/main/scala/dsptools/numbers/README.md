> Note: Type classes require that you `import dsptools.numbers.implicits._`
> To support the operations below for DspReal, FixedPoint, SInt, UInt, you want a: T where `[T <: Data:RealBits]`
> If you want to support IsIntegral operations for SInt + UInt (in addition to the others), you want a: T where `[T <: Data:IntegerBits]`
> DspReal is *not* synthesizable!

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


TODO:
Lits in general (type class + not) i.e. 5.S, etc. + fixed width
Complex: apply, proto, protoWithFixedWidth  
General declarations (fixed width)
ConvertableTo
ConvertableFrom
DspContext: How to use + contents