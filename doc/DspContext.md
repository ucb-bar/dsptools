BIG WARNING: If you want to directly use UInt, SInt, & FixedPoint without passing them through some generic, you **should not** use the Ring operators +, -, *, unary_- *if* you want to use DspContext. Using + on a normal UInt will result in Chisel + behavior (wrapped addition). To guarantee that the Ring operators follow DspContext, after importing implicits, you should instead use:
* a context_+ b
* a context_- b
* a context_* b
* a.context_unary_- 

We need to come up with better names, but at least this makes it easy to search for context_...

# DspContext
DspContext allows you to control how some operations are performed within a scope.
```
  DspContext.alter(newContext) {
    // new scope with settings from newContext
  }
```
There are a number of helpers that can be used to set one field of the context, e.g. `DspContext.withTrimType`.

The `DspContext` case class contains the following fields:
* `overflowType` (type: `OverflowType`; default: `Grow`) specifies overflow behavior for ops like a + b, a - b, -a
  * `Saturate` (not implemented)
  * `Wrap`: Wrap output on overflow (output has max width of either input)
  * `Grow`: Add MSBs so your output is numerically correct
* `trimType` (type: TrimType; default: Floor) specifies how Fixed point ops like a * b, a.trimBinary(n), and a.div2(n) should round results
  * `NoTrim`: Keep maximal precision + bit growth
  * `Truncate` (not implemented)
  * `RoundHalfUp`: Assumes binaryPoints are well defined,
    * For `times` and `div2`: Rounds half up to a.binaryPoint.get.max(b.binaryPoint.get) + DspContext.binaryPointGrowth # of fractional bits -- looks at the result's next bit
    * For trimBinary: Rounds half up to n fractional bits
    * **WARNING**: The overflow behavior when you try to round up the largest representable positive FixedPoint value is defined by DspContext.overflowType. It's only guaranteed to be mathematically correct if you grow!
  * `Floor`: Rounds towards negative infinity; # of fractional bits is the same as in RoundHalfUp case
  * Caution: Any time a known binary point is expected, you might run into Chisel/Firrtl crashes if an unknown binary point creeps in. Please let us know if you are running into issues with library code.
* `binaryPointGrowth` (type: Int; default: 1)
  * Use case explained above
  * Requires that the input binary point is well defined
* `binaryPoint` (type: `Option[Int]`; default: `Some(14)`)
  * Specifies the default # of fractional bits when creating `FixedPoint` literals with something like `ConvertableTo[T].fromDouble(3.14)`
* `numBits` (type: `Option[Int]`; default: `Some(16)`, unused)
* `complexUse4Muls` (type: Boolean, default: false)
  * true: Use 4 real multiplies to perform a complex multiply
  * false: Use 3 real multiplies to perform a complex multiply
* `numMulPipes` (type: `Int`; default: 0)
  * # of pipeline registers to add after a multiply operation between two inputs of type `[T <: Data:RealBits]`
  * Note: This only applies to multiplications with `[T <: Data:RealBits]`. `DspComplex` multiplication utilizes some combination of this and `numAddPipes`
* `numAddPipes` (type: `Int`; default: 0)
  * # of pipeline registers to add after an add operation between two inputs of type `[T <: Data:RealBits]`

## How to Use
* You must `import dsptools.numbers._`
* You can change the DspContext @ any level (Top, Module, local operations) based off of where you wrap the change i.e. what you surround by `DspContext.with...{ ... }` 
* **Example:** Changing the local +, - overflow behavior (while keeping other options the same; only for the operations inside the braces -- otherwise use defaults)
  * `val sum = DspContext.withOverflowType(Wrap) { a context_+ b }`
  * `val prod = DspContext.withTrimType(RoundHalfUp) { a context_* b }`
  * `val prod = DspContext.withBinaryPointGrowth(3) { a context_* b }`
  * `val lit = DspContext.withBinaryPoint(8) { ConvertableTo[FixedPoint].fromDouble(3.14) }`
  * `val prod = DspContext.withComplexUse4Muls(true) { ca context_* cb }`
  * `val prod = DspContext.withNumMulPipes(2) { a context_* b }`
  * `val sum = DspContext.withNumAddPipes(1) { a context_+ b }`
* **Example:** Change several options locally:
  ```
  val prod = DspContext.alter(DspContext.current.copy(trimType = NoTrim, binaryPointGrowth = 3, numMulPipes = 2)) { a * b }
  ```
* Get the number pipeline registers used in a Complex multiply:
  * `val delay = DspContext.current.complexMulDly`

