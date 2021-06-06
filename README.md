DSP Tools Development Environment
===================

[![Test](https://github.com/ucb-bar/dsptools/actions/workflows/test.yml/badge.svg)](https://github.com/ucb-bar/dsptools/actions/workflows/test.yml)

This repository serves as a good starting point for making and easily testing your various DSP
 generators in Chisel *(1 generator at a time)*. See [UC Berkeley Chisel](https://chisel.eecs.berkeley.edu) homepage for more information about Chisel.

For a list of common errors, check out the [wiki page](https://github.com/ucb-bar/dsptools/wiki/Common-Errors).
Feel free to add your own!

----------

Key Enhancements
===============

Dsptools is a library that can be used with any Chisel library.
Some of the goals of dsptools are to enable:

 1. Pipeline delay checking (Isn't it annoying when the delays of two signals into an operation don't line up because you forgot to delay a corresponding signal in your haste to close timing?)

 2. Enhanced support for designing and testing DSP with generic types (i.e. switching between **DSPReal** for verifying functional correctness with double-precision floating point and **FixedPoint** for evaluating fixed-point design metrics by changing a single parameter).

 3. More useful and universal testing platform for numeric types!
 > Numbers are displayed in their correct formats instead of hex for peek, poke, and expect operations. Additionally, if your tester extends **DSPTester**, you can optionally dump your test sequence to a **Verilog testbench** that replays the test for functional verification on all simulation platforms (i.e. Xilinx, Altera, etc. instead of only VCS). The tolerance of comparisons with expected values can also be changed via `DSPTester.setTol(floTol = decimal_tolerance,
                     fixedTol = number_of_bits)`.

 4. **Miscellaneous additional features**
 - Wide range of LUT modules for ease of generating lookup tables from pre-calculated constants (no intermediate representation)
 - Memory modules that abstract out confusion associated with Chisel Mem
 - Generates useful helper files with each Verilog output (constraints, generator parameters used, etc.).
 - Easier to rename modules & signals and have renaming actually succeed.
 - Expanding Support for non-base-2 math.
 - Adds support for numerical processing in the Chisel Environment via [Breeze](https://github.com/scalanlp/breeze).

----------

Getting Started
===============

Dsptools is published alongside Chisel, FIRRTL, and the other related projects.
It can be used by adding

```scala
libraryDependencies += "edu.berkeley.cs" %% "dsptools" % "XXXX"
```

to your build.sbt, where `XXXX` is the desired version.
See Github for the latest release.
Snapshots are also published on Sonatype, which are beneficial if you want to use the latest features.

Projects that dsptools depends on are:

- [FIRRTL](https://github.com/ucb-bar/firrtl)

- [FIRRTL Interpreter](https://github.com/ucb-bar/firrtl-interpreter)

- [Chisel3](https://github.com/ucb-bar/chisel3)

- [Chisel Testers](https://github.com/ucb-bar/chisel-testers)

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

**For a additional, more detailed description of the Numeric classes in dsptools: see [The Numbers ReadMe](https://github.com/ucb-bar/dsptools/blob/master/src/main/scala/dsptools/numbers/README.md)**


A generic function in scala is defined like so:

```def func[T](in: T): T```

This means that you can call `func(obj)` for an object of any type. If `obj` is of type `Q`, you can write `func[Q](obj)` to specify that we want the `Q` version of the generic function `func`, but this is only necessary if the scala compiler can't figure out what `Q` is supposed to be.

You can also write

```class SomeClass[T]```

and use `T` like it is a real type for any member functions of variables.
To write a generic chisel Module, we might try to write

```
class Passthrough[T](gen: T) extends Module {
  val io = new IO(Bundle {
    val in = Input(gen)
    val out = Output(gen)
  })
  io.out := io.in
}
```

Here, `gen` is a parameter specifying the type you want to use for your IO's, so you could write `Module(new Passthrough(SInt(width=10)))` or `Module(new Passthrough(new Bundle { ... }))`.
Unfortunately, there's a problem with this.
`T` can be any type, and a lot of types don't make sense, like `String` or `()=>Unit`.
This will not compile, because `Input()`, `Output()`, and `:=` are functions defined on chisel types.
We can fix this problem by writing

```class Passthrough[T<:Data](gen: T) extends Module```

This type constraint means that we have to choose `T` to be a subtype of the chisel type `Data`.
Things like `UInt`, `SInt`, and `Bundle` are subtypes of `Data`.
Now the example above should compile.
This example isn't very interesting, though.
`Data` lets you do basic things like assignment and make registers, but doesn't define any mathematical operations, so if we write

```
class Doubler[T<:Data](gen: T) extends Module {
  val io = IO(new Bundle {
    val in = Input(gen)
    val out = Output(gen)
  })
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
Typeclasses are a useful pattern in scala, so there is syntactic sugar to make using them easy:

```
import dsptools.numbers._
class Doubler[T<:Data:Real](gen: T) extends Module
```

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
    - defines === and =/= (returning chisel Bools!)
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

Rocket-chip
===============
Integration of dsptools with a rocket-chip based project:

The github project [Rocket Dsp Utils](https://github.com/chick/rocket-dsp-utils) contains useful tools
that can be used to integrate components from this project with a rocket-chip based one.

These tools formerly were contained in this repo under the `rocket` sub-directory.

----------

This code is maintained by [Chick](https://github.com/chick), [Angie](https://github.com/shunshou) and [Paul](https://github.com/grebe). Let us know if you have any questions/feedback!

Copyright (c) 2015 - 2021 The Regents of the University of California. Released under the Modified (3-clause) BSD license.
