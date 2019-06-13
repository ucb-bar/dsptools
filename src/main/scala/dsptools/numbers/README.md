> Note: Type classes require that you `import dsptools.numbers.implicits._`
> To support the operations below for DspReal, FixedPoint, SInt, UInt, you want a: T where `[T <: Data:RealBits]`
> If you want to support IsIntegral operations for SInt + UInt (in addition to the others), you want a: T where `[T <: Data:IntegerBits]`
> DspReal is *not* synthesizable!

BIG WARNING: If you want to directly use UInt, SInt, & FixedPoint without passing them through some generic, you **should not** use the Ring operators +, -, *, unary_- *if* you want to use DspContext. Using + on a normal UInt will result in Chisel + behavior (wrapped addition). To guarantee that the Ring operators follow DspContext, after importing implicits, you should instead use:
* a context_+ b
* a context_- b
* a context_* b
* a.context_unary_- 

We need to come up with better names, but at least this makes it easy to search for context_...


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
      * **WARNING**: The overflow behavior when you try to round up the largest representable positive FixedPoint value is defined by DspContext.overflowType. It's only guaranteed to be mathematically correct if you grow!
    * Floor: Rounds towards negative infinity; # of fractional bits is the same as in RoundHalfUp case
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
  * You can change the DspContext @ any level (Top, Module, local operations) based off of where you wrap the change i.e. what you surround by `DspContext.with...{ whatever_code_you_want_to_use_this_context }` where { might be found at the top of a module and } might be at the bottom,e tc.
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
  * a.mulj()
    * Multiplies the complex value a by j
  * a.divj()
    * Divides the complex value a by j
  * a.conj()
    * Returns the complex conjugate of a
  * a.abssq()
    * Returns the squared norm of a (If a = x + y * i, returns x^2 + y^2)
    * Context behavior goverened by the context behavior of add and multiply operations above (See Ring)

# Special operations supported by T of UInt, SInt, FixedPoint, DspReal for type conversion [ChiselConvertableFrom type class]
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

A basic DSP Module + Tester might look like this:

```scala
package SimpleDsp

// Allows you to use Chisel Module, Bundle, etc.
import chisel3._
// Allows you to use FixedPoint
import chisel3.experimental.FixedPoint
// If you want to take advantage of type classes >> Data:RealBits (i.e. pass in FixedPoint or DspReal)
import dsptools.numbers.{RealBits}
// Required for you to use operators defined via type classes (+ has special Dsp overflow behavior, etc.)
import dsptools.numbers.implicits._
// Enables you to set DspContext's for things like overflow behavior, rounding modes, etc.
import dsptools.DspContext
// Use DspTester, specify options for testing (i.e. expect tolerances on fixed point, etc.)
import dsptools.{DspTester, DspTesterOptionsManager, DspTesterOptions}
// Allows you to modify default Chisel tester behavior (note that DspTester is a special version of Chisel tester)
import iotesters.TesterOptions
// Scala unit testing style
import org.scalatest.{FlatSpec, Matchers}

// IO Bundle. Note that when you parameterize the bundle, you MUST override cloneType.
// This also creates x, y, z inputs/outputs (direction must be specified at some IO hierarchy level)
// of the type you specify via gen (must be Data:RealBits = UInt, SInt, FixedPoint, DspReal)
class SimpleDspIo[T <: Data:RealBits](gen: T) extends Bundle {
  val x = Input(gen.cloneType)
  val y = Input(gen.cloneType)
  val z = Output(gen.cloneType)
  override def cloneType: this.type = new SimpleDspIo(gen).asInstanceOf[this.type]
}

// Parameterized Chisel Module; takes in type parameters as explained above
class SimpleDspModule[T <: Data:RealBits](gen: T, val addPipes: Int) extends Module {
  // This is how you declare an IO with parameters
  val io = IO(new SimpleDspIo(gen))
  // Output will be current x + y addPipes clock cycles later
  // Note that this relies on the fact that type classes have a special + that
  // add addPipes # of ShiftRegister after the sum. If you don't wrap the sum in 
  // DspContext.withNumAddPipes(addPipes), the default # of addPipes is used.
  DspContext.withNumAddPipes(addPipes) { 
    io.z := io.x + io.y
  }
}

// You create a tester that must extend DspTester to support Dsp type peeks/pokes (with doubles, complex, etc.)
class SimpleDspModuleTester[T <: Data:RealBits](c: SimpleDspModule[T]) extends DspTester(c) {
  val x = Seq(-1.1, -0.4, 0.4, 1.1)
  val z = x map (2 * _)
  for (i <- 0 until (x.length + c.addPipes)) {
    val in = x(i % x.length)
    // Feed in to the x, y inputs
    poke(c.io.x, in)
    // Locally (just for the stuff in {}) change console print properties
    // so that this second peek isn't displayed on the console 
    // (since the input value is the same as the first peek)
    updatableDspVerbose.withValue(false) {
      poke(c.io.y, in)
    }
    if (i >= c.addPipes) {
      // Expect that the z output matches the expected value @ z(i - c.addPipes) to some tolerance
      // as described below
      expect(c.io.z, z(i - c.addPipes))
    }
    // Step the clock by 1 period
    step(1)
  }
}

// Scala style testing
class SimpleDspModuleSpec extends FlatSpec with Matchers {
  
  // If you don't want to use default tester options, you need to create your own DspTesterOptionsManager
  val testOptions = new DspTesterOptionsManager {
    // Customizing Dsp-specific tester features (unspecified options remain @ default values)
    dspTesterOptions = DspTesterOptions(
        // # of bits of error tolerance allowed by expect (for FixedPoint, UInt, SInt type classes)
        fixTolLSBs = 1,
        // Generate a Verilog testbench to mimic peek/poke testing
        genVerilogTb = true,
        // Show all tester interactions with the module (not just failed expects) on the console
        isVerbose = true)
    // Customizing Chisel tester features
    testerOptions = TesterOptions(
        // If set to true, prints out all nested peeks/pokes (i.e. for FixedPoint or DspReal, would
        // print out BigInt or base n versions of peeks/pokes -- rather than the proper decimal representation)
        isVerbose = false,
        // Default backend uses FirrtlInterpreter. If you want to simulate with the generated Verilog,
        // you need to switch the backend to Verilator. Note that tests currently need to be dumped in 
        // different output directories with Verilator; otherwise you run into weird concurrency issues (bug!)...
        backendName = "verilator")
    // Override default output directory while maintaining other default settings
    commonOptions = commonOptions.copy(targetDirName = "test_run_dir/simple_dsp_fix")
  }

  behavior of "simple dsp module"

  it should "properly add fixed point types" in {
    // Run the dsp tester by following this style: You need to pass in the Chisel Module [SimpleDspModule] 
    // to test and your created DspTesterOptionsManager [testOptions]. You must also specify the tester 
    // [SimpleDspModuleTester] to run with the module. This tester should be something that extends DspTester. 
    // Note that here, you're testing the module with inputs/outputs of FixedPoint type (Q15.12) 
    // and 3 registers (for retiming) at the output. You could alternatively use DspReal()
    // Scala keeps track of which tests pass/fail; the execute method returns true if the test passes. 
    // Supposedly, Chisel3 testing infrastructure might be overhauled to reduce the amount of boilerplate, 
    // but this is currently the endorsed way to do things.
    dsptools.Driver.execute(() => new SimpleDspModule(FixedPoint(16.W, 12.BP), addPipes = 3), testOptions) { c =>
      new SimpleDspModuleTester(c)
    } should be (true)
  }

}
```

Please read the above code + comments carefully. 

It shows you how to create a parameterized module + IO bundle (API might change again...) with generic type classes to allow you to test your "math" both with real numbers (that result in numerically correct outputs) and fixed point numbers (that allow you to factor in quantization, etc. and are actually synthesizable). Note the need of cloneType for parameterized bundles!

It also demonstrates a simple example of changing the Dsp Context. You can do this locally (per operation), at the module level, or at the top level (simply affected by where you wrap the DspContext.withNumAddPipes(addPipes) {}).

The example also shows you how a tester interacts with the DUT via peek and expect and how to change tester options like expect tolerances. You can change tester options globally via what's passed in to the DspTesterOptionsManager or you can change some of them (for example, display) locally -- just for some portions of the tester operation. 

To run this single test, you can use the command `sbt "testOnly SimpleDsp.SimpleDspModuleSpec"`. Note that `sbt test` runs all tests in *src/test/scala*.

---

# Details on DspTesterOptionsManager
* As in the example above, the DspTesterOptionsManager manages the following category of options listed hierarchically [objectName = caseClass] (Case class files linked):
  * dspTesterOptions = DspTesterOptions() [file](https://github.com/ucb-bar/dsptools/blob/add_ops/src/main/scala/dsptools/tester/DspTesterOptions.scala)
  * testerOptions = TesterOptions() [file](https://github.com/ucb-bar/chisel-testers/blob/master/src/main/scala/chisel3/iotesters/TesterOptions.scala)
  * interpreterOptions = InterpreterOptions() [file](https://github.com/ucb-bar/firrtl-interpreter/blob/master/src/main/scala/firrtl_interpreter/Driver.scala)
  * chiselOptions = ChiselExecutionOptions() [file](https://github.com/ucb-bar/chisel3/blob/master/src/main/scala/chisel3/ChiselExecutionOptions.scala)
  * firrtlOptions = FirrtlExecutionOptions() [file](https://github.com/ucb-bar/firrtl/blob/master/src/main/scala/firrtl/ExecutionOptionsManager.scala)
  * commonOptions = CommonOptions() [file](https://github.com/ucb-bar/firrtl/blob/master/src/main/scala/firrtl/ExecutionOptionsManager.scala)
    * All XOptionsManager s have this.
* Default options are used if you don't reassign to the object (var), which is already part of the DspTesterOptionsManager
* Below, I've listed the options + their defaults that you'll likely change for some of the case classes mentioned above (for the full list of options, look through the linked files). See the example above on how to override defaults.
  * DspTesterOptions
    * isVerbose
      * Default = true
      * Top-level DspTester verbosity (peek, poke, expect, step, reset prints on console)
    * fixTolLSBs
      * Default = 0 (Int)
      * Expect tolerance in terms of LSBs for FixedPoint, SInt, UInt
    * realTolDecPts
      * Default = 8 (Int)
      * 10^(-realTolDecPts) toerance for expect on DspReal
    * genVerilogTb
      * Default = false
      * Generate Verilog TB (in the target directory and called tb.v) that mirrors Chisel tester's peek/poke/expect/step/reset for use with outside simulators
    * clkMul
      * Default = 1 (Int)
      * clock period in ps = clkMul * tbTimeUnitPs
    * tbTimeUnitPs
      * Default = 100 (Int)
      * Test bench time unit in ps
    * tbTimePrecisionPs
      * Default = 10 (Int)
      * Time precision in ps
    * initClkPeriods
      * Default = 5 (Int)
      * # clock periods for initial reset (for use with TB generation)
    * inOutDelay
      * Default = 0.5 (Double) i.e. half a clock period
      * Input/output delay after which to peek/poke values (some fraction of clkMul)
      * Note that this # affects whether the TB will pass post-synthesis or post-PR and should match the input/output delay constraints #
  * TesterOptions
    * isVerbose
      * Default = false
      * Whether to print out sub-peek/poke/expect info onto the console (i.e. for Complex, prints the individual real/imaginary peek or for UInt, print with the default PeekPokeTester representation via displayBase)
    * displayBase
      * Default = 10 (Int)
      * Base to print out sub-peek/poke/expect info (converts raw bits into the specified base)
    * backendName
      * Default = "firrtl"
      * Either "firrtl" for Firrtl-Interpreter or "verilator" for Verilator (preferred) [also supports VCS? untested]
  * FirrtlExecutionOptions
    * compilerName
      * Default = "verilog"
      * If using the testers, this will be automatically set to "low" with Firrtl-Interpreter and "verilog" with Verilator
      * If you want to only compile your code without running tests, you can use something like `chisel3.Driver.execute(optionsManager, dut)` where dut is a Chisel Module and optionsManager is of type: *ExecutionOptionsManager with HasChiselExecutionOptions with HasFirrtlOptions*. This optionsManager also has commonOptions.
      * When only doing compilation, this supports outputting the circuit in "high", "middle", or "low" Firrtl or "verilog".
    * customTransforms - Seq[Transform]
      * Default = Empty
      * See below for an example
      * Order matters
    * annotations - List[Annotation]
      * Default = Empty
      * See below for an example (tends to go hand in hand with customTransforms + this method is really only for InferReadWrite and ReplSeqMem -- an alternative method is currently being made for more tapeout-y Firrtl transforms)
  * CommonOptions
    * targetDirName
      * If not specified, test outputs will be dumped to test_run_dir, otherwise, outputs will be dumped in specified directory, as in the example above!   

Let's say you create a Chisel Module called CustomMemory (circuit class name) which contains some SeqMem's that you want to replace with black boxes + infer readwrite ports so you can use single-ported SRAMs instead of dual-ported ones if reads are deemed to never occur simultaneously with writes. Two Firrtl transforms can do this for you: ReplSeqMem and InferReadWrite. In order to run these transforms, you'll need to use custom FirrtlExecutionOptions. Note that black box substitution should only occur when compiling (and not testing) the Chisel code, *unless* you provide a black box resource to match the outputted black boxes. Otherwise, Verilator won't be able to find the memory black boxes, and it'll be sad. 

Note: A configuration file to be able to run the ucb-bar vlsi_mem_gen Python script is output with ReplSeqMem. The file name/location is, in this case, vlsi_mem_gen.conf in your top level directory (you need to specify the location as opposed to just the name; this is legacy because "targetDirName" didn't exist when the transform was written)

Your code (either inside a Scala test Spec if you want to run `sbt test` or inside a Scala main function if you want to run `sbt run`) might look something like this:

```
  val opts = new ExecutionOptionsManager("chisel3") with HasChiselExecutionOptions with HasFirrtlOptions {
    firrtlOptions = FirrtlExecutionOptions(
      compilerName = "verilog",
      customTransforms = Seq(
        new passes.memlib.InferReadWrite(),
        new passes.memlib.ReplSeqMem()),
      annotations = List(
        passes.memlib.InferReadWriteAnnotation("CustomMemory"),
        passes.memlib.ReplSeqMemAnnotation(s"-c:CustomMemory:-o:vlsi_mem_gen.conf"))
    )
  }

  chisel3.Driver.execute(opts, () => new CustomMemory())

```

---

# DspTester Functions
* step(n)
  * Step the clock forward by n cycles
* reset(n)
  * Hold reset for n clock cycles
* poke(input, value) -- Input, Value types given below
  * Bool, Boolean
  * UInt, Int -- prints width
  * SInt, Int -- prints width
  * FixedPoint, Double -- prints Qn.m notation
  * DspReal, Double
  * T <: Data:RealBits, Double
  * DspComplex[T], breeze.math.Complex -- prints a + bi
* peek(node) -- Node, Output types given below
  * Bool, Boolean
  * UInt, Int -- prints width
  * SInt, Int -- prints width
  * FixedPoint, Double -- prints Qn.m notation
  * DspReal, Double
  * T <: Data:RealBits, Double
  * DspComplex[T], breeze.math.Complex -- prints a + bi
* expect(node, expectedValue, message: String) -- Node, expectedValue types given below 
  * Returns true if node value = expectedValue, otherwise false
  * message is optional
  * Prints tolerance
  * Will always print if the expect failed (regardless of display flag)
  * Bool, Boolean
  * UInt, Int -- prints width
  * SInt, Int -- prints width
  * FixedPoint, Double -- prints Qn.m
  * DspReal, Double
  * T <: Data:RealBits, Double
  * DspComplex[T], breeze.math.Complex -- prints a + bi

# Other Tester Functions (from PeekPokeTester)
* pokeAt[T <: Bits](memory: Mem[T], value: BigInt, offset: Int)
  * Updates memory[offset] = value via tester, not valid when a Verilog TB is printed
  * Not something you should usually use with DspTester, since everything is BigInt bit representation
* peek(signal: Aggregate): IndexedSeq[BigInt]
  * Peeks elements of a generic Aggregate (Bundle, Vec) one by one and returns the bit representation as a BigInt
  * Not something you should usually use with DspTester
* peekAt[T <: Bits](memory: Mem[T], offset: Int): BigInt
  * Peeks the value at memory[offset] as a BigInt bit representation
  * Not something you should usually use with DspTester
* expect(pass: Boolean, message: String)
  * Prints PASS if pass is true, otherwise FAIL, along with message

Example console printout:

```
STEP 5x -> 16
  POKE SimpleIOModule.io_i_vU_0 <- 3.0, 8-bit U
  POKE SimpleIOModule.io_i_vS_0 <- 3.3, 8-bit S
  POKE SimpleIOModule.io_i_vF_0 <- 3.3, Q3.4
  PEEK SimpleIOModule.io_o_vU_0 -> 3.0, 8-bit U
  EXPECT SimpleIOModule.io_o_vU_0 -> 3.0 == E 3.0 PASS, tolerance = 0.0, 8-bit U
  PEEK SimpleIOModule.io_o_vS_0 -> -3.0, 8-bit S
  EXPECT SimpleIOModule.io_o_vS_0 -> -3.0 == E -3.0 PASS, tolerance = 0.0, 8-bit S
  PEEK SimpleIOModule.io_o_vF_0 -> -3.3125, Q3.4
  EXPECT SimpleIOModule.io_o_vF_0 -> -3.3125 == E -3.3 PASS, tolerance = 0.0625, Q3.4
```

A couple of the DspTesterOptions can be updated locally within your DspTester by wrapping the region in which the change should apply in much the way DspContext changes were done. Earlier code showed you how to update the verbosity locally. You can locally change the verbosity for debugging. Additionally, you can locally weaken or strengthen expect tolerances if you know certain operations are more prone to quantization error, etc. 

* DspTesterOptions.isVerbose
  * `updatableDspVerbose.withValue(false) { /* code here */ }`
* TesterOptions.isVerbose
  * `updatableSubVerbose.withValue(true) { /* code here */ }`
* TesterOptions.displayBase
 * `updatableBase.withValue(2) { /* code here */ }`
* DspTesterOptions.fixTolLSBs
 * `fixTolLSBs.withValue(3) { /* code here */ }`
* DspTesterOptions.realTolDecPts
 * `realTolDecPts.withValue(5) { /* code here */ }`

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

