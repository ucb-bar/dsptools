DSP Tools Development Environment
===================

> Note: The directory structure is in flux. We're in the process of refactoring. :) Unfortunately, that means the setup instructions will be temporarily out-of-date again :(...

This repository hopefully serves as a good starting point for making and easily testing your various DSP
 generators *(1 generator at a time)*. See [UC Berkeley Chisel](https://chisel.eecs.berkeley.edu) homepage for more information about Chisel.

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

This code is maintained by [Chick](https://github.com/chick) [Angie](https://github.com/shunshou) and [Paul](https://github.com/grebe). Let us know if you have any questions/feedback!

Copyright (c) 2015 - 2016 The Regents of the University of California. Released under the Modified (3-clause) BSD license.
