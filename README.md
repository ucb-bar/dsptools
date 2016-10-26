DSP Tools Development Environment
===================

> Note: The directory structure is in flux. We're in the process of refactoring. :) Unfortunately, that means the setup instructions will be temporarily out-of-date again :(...

This repository hopefully serves as a good starting point for making and easily testing your various DSP
 generators *(1 generator at a time)*. See [UC Berkeley Chisel](https://chisel.eecs.berkeley.edu) homepage for more information about Chisel.

For a list of common errors, check out the [wiki page](https://github.com/ucb-bar/dsptools/wiki/Common-Errors).
Feel free to add your own!

----------

Key Enhancements
===============

Key DSP library enhancements over base Chisel (albeit at the expense of coding style restrictions & verbosity--enforces *good practice*!):

 1. Pipeline delay checking (Isn't it annoying when the delays of two signals into an operation don't line up because you forgot to delay a corresponding signal in your haste to close timing?)

 2. Enhanced support for designing and testing DSP with generic types (i.e. switching between **DSPDbl** for verifying functional correctness with double-precision floating point and **DSPFixed** for evaluating fixed-point design metrics by changing a single **sbt run** parameter).
> Inside any class that extends **GenDSPModule**, any `gen` will conform to the `Fixed=true/false` option used when running `make`. To create a new IO or internal node of type **gen** with `fixedParams=(integerWidth,fractionalWidth)`, use `gen.cloneType(fixedParams)` where the arguments are optional (defaults to integer and fractional widths indicated in the JSON file). If you want to specify a literal (or constant) of type **gen** within your module, use `double2T(yourConstant,fixedParams)`. Likewise, you can leave out *fixedParams* if you want to use defaults.

 3. Supports parameterization from external sources via JSON (i.e. in theory, configuration options for your generator can be passed in from a web interface, like [Spiral](http://www.spiral.net/hardware/dftgen.html)). This is achieved with the help of [Json4s](http://json4s.org).

 4. More useful and universal testing platform for numeric types!
 > Numbers are displayed in their correct formats instead of hex for peek, poke, and expect operations. Additionally, if your tester extends **DSPTester**, you can optionally dump your test sequence to a **Verilog testbench** file for functional verification on all simulation platforms (i.e. Xilinx, Altera, etc. instead of only VCS). The tolerance of comparisons with expected values can also be changed via `DSPTester.setTol(floTol = decimal_tolerance,
                     fixedTol = number_of_bits)`.

 5. **Miscellaneous additional features**
	 - Wide range of LUT modules for ease of generating lookup tables from pre-calculated constants (no intermediate representation)
	 - Memory modules that abstract out confusion associated with Chisel Mem
	 - Generates useful helper files with each Verilog output (constraints, generator parameters used, etc.).
	 - Easier to rename modules & signals and have renaming actually succeed.
	 - Expanding Support for non-base-2 math.
	 - Adds support for numerical processing in the Chisel Environment via [Breeze](https://github.com/scalanlp/breeze).

----------

Getting Started
===============

This package is under intensive development right now. Changes are happenging quickly and there a dependencies on
several different branches of related projects.  
Here is a simple manual way to build a running system

- create a directory for the projects

- Build Firrtl
    - `git clone http://github.com/ucb-bar/firrtl.git`
    - `git checkout add-fixed-point-type`
    - `sbt publish-local`
    
- Build Interpreter
    - `git clone http://github.com/ucb-bar/firrtl-interpreter.git`
    - `git checkout dsp-real-support`
    - `sbt publish-local`
    
- Build Chisel3
    - `git clone http://github.com/ucb-bar/chisel3.git`
    - `git checkout dsp-support-1`
    - `sbt publish-local`

- Build Chisel testers
    - `git clone http://github.com/ucb-bar/chisel-testers.git`
    - `sbt publish-local`
    
- Build dsptools
    - `git clone http://github.com/ucb-bar/dsptools.git`
    - sbt test

In the future, these steps will be automated with sbt

----------

Numeric Typeclasses
===============

This library defines a number of typeclasses for numeric types.
A brief explanation of how typeclasses work in scala can be found [here](http://typelevel.org/cats/typeclasses.html) and [here](http://blog.jaceklaskowski.pl/2015/05/15/ad-hoc-polymorphism-in-scala-with-type-classes.html).
Our DSP-specific typeclasses are built on top of [spire](https://github.com/non/spire).

The goal of these typeclasses is to make it easy to write chisel modules that treat the number representation as a parameter.
For example, using typeclasses you can write chisel that generates an FIR filter for both real and complex numbers.
You can also use typeclasses to write chisel that generates a circuit implementation using floating point (via Verilog's real type).
After testing that your circuit implementation works with floating point, you can use the same code to generate a fixed point version of the circuit suitable for synthesis.

A generic function in scala is defined via

```def func[T](in: T): T```

This means that you can call `func(obj)` for an object of any type. If `obj` is of type `Q`, you can write `func[Q](obj)` to specify that we want the `Q` version of the generic function `func`, but this is only necessary if the scala compiler can't figure out what `Q` is supposed to be.

You can also write

```class SomeClass[T]```

and use `T` like it is a real type for any member functions of variables.
To write a generic chisel Module, we might try to write

```
class Passthrough[T](gen: => T) extends Module {
  val io = new Bundle {
    val in = gen.asInput
    val out = gen.asOutput
  }
  io.out := io.in
}
```

Here, `gen` is a parameter specifying the type you want to use for your IO's, so you could write `Module(new Passthrough(SInt(width=10)))` or `Module(new Passthrough(new Bundle { ... }))`.
Unfortunately, there's a problem with this.
`T` can be any type, and a lot of types don't make sense, like `String` or `()=>Unit`.
This will not compile, because `.asInput, `.asOutput`, and `:=` are functions defined on chisel types.
We can fix this problem by writing

```class Passthrough[T<:Data](gen: => T) extends Module```

This means that we have to choose `T` to be a subtype of the chisel type `Data`.
Things like `UInt`, `SInt`, and `Bundle` are subtypes of `Data`.
Now the example above should compile.
This example isn't very interesting, though.
`Data` lets you do basic things like assignment and make registers, but doesn't define any mathematical operations, so if we write

```
class Doubler[T<:Data](gen: => T) extends Module {
  val io = new Bundle {
    val in = gen.asInput
    val out = gen.asOutput
  }
  io.out := io.in + io.in
}
```

it won't compile.
This is where typeclasses come in.
This library defines a trait

```
trait Real[T] {
  ...
  def plus(x: T, y: T): T
  ...
}
```

as well as an implicit conversion so that `a+b` gets converted to `Real[T].plus(a,b)`.
`Real[T]` is a typeclass.
Typeclasses are a useful pattern in scala, so there is nice concise syntax to make using them easy:

```
import dsptools.numbers.implicits._
class Doubler[T<:Data:Real](gen: => T) extends Module
```

(Including the `implicits._` object is important, otherwise the implicit conversion from `io.in + io.in` to `Real[T].plus(io.in, io.in)` won't work).

Note: If you don't include the `:Real` at the end, the scala compiler will think `io.in + io.in` is string concatenation and you'll get a weird error saying

```
[error]  found   : T
[error]  required: String
```

Some useful typeclasses:
- Ring
    - defines +, *, -, **, zero, one
    - defined in [Spire](https://github.com/non/spire)
    - Read: https://en.wikipedia.org/wiki/Ring_(mathematics)
    - Note: We chose to restrict ourselves to `Ring` rather than `Field` because division is particularly expensive and nuanced in hardware. Rather than typing `a / b` we think it is better to require users to instantiate a module and think about what's going on.

- Eq
    - defines === and =!= (returning chisel Bools!)
- PartialOrder
    - extends Eq
    - defines >, <, <=, >= (returning a `ValidIO[ComparisonBundle]` that has `valid` false if the objects are not comparable
- Order
    - extends PartialOrder
    - defines >, <, <=, >=, min, max
- Sign
    - defines abs, isSignZero, isSignPositive, isSignNegative, isSignNonZero, isSignNonPositive, isSignNonNegative
- Real
    - extends Ring with Order with Sign
    - defines ceil, round, floor, isWhole
    - defines a bunch of conversion methods from ConvertableTo, e.g. fromDouble, fromInt
- Integer
    - extends Real
    - defines mod

----------

This code is maintained by [Chick](https://github.com/chick) [Angie](https://github.com/shunshou) and [Paul](https://github.com/grebe). Let us know if you have any questions/feedback!

Copyright (c) 2015 - 2016 The Regents of the University of California. Released under the Modified (3-clause) BSD license.
