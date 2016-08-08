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

1. Follow directions for starting a new chisel project [here](https://github.com/ucb-bar/chisel-template)

----------

Testing & Verilog Generation
===============

- `make debug PRJ=☆ FIXED=true/false` runs your instance of **DSPTester** and displays results on the console. The version of the module tested either uses *DSPFixed* or *DSPDbl* for *gen* depending on *FIXED*.

- `make asic PRJ=☆` generates a version of the Verilog RTL that is best suited for ASICs. Essentially, memories are blackboxed and a script in **ChiselEnvironment/VerilogHelpers/vlsi_mem_gen** is used to replace the black boxes with **SRAM** from an appropriate technology. Note that the script is not publicly available as it is technology/vendor specific. The output is located in **ChiselEnvironment/ChiselPrj☆/asic/**. The files generated are:
     - **☆.conf** details SRAM configuration i.e. depth, width, & ports for all memories required (used by *vlsi_mem_gen*)
     - **☆.v** is your top module's Verilog RTL (with SRAMs swapped in)
     - **generator_out.json** saves the parameters used for this generated design
     - **Makefrag_prj** is a helper file that specifies the name of the top module and clock period in ns (which can be included in the *Makefile* of your *vlsi* setup)

- `make asic_tb PRJ=☆` does the same as `make asic`, but also runs `make debug` and generates an additional **tb.v**, which is your Chisel testbench translated into a Verilog testbench (with fixed-point IO).

- `make fpga PRJ=☆` generates a version of the Verilog RTL that is best suited for FPGAs. The memories are written as **registers**, and BRAM can be inferred. The output is located in **ChiselEnvironment/ChiselPrj☆/fpga/**. The files generated are:
     - **constraints.xdc** specifies the clock constraints used for synthesis & place/route
     - **☆.v** is your top module's Verilog RTL (where registers are used for memory)
     - **generator_out.json**: See above.

- `make fpga_tb PRJ=☆` does the same as `make fpga`, but also runs `make debug` and generates a **tb.v**.

> **Note #1:** You should never write your own code into **ChiselEnvironment/ChiselPrj☆/fpga/** or **ChiselEnvironment/ChiselPrj☆/asic/**. They will be overwritten.
>
> **Note #2:** To save you some typing, whenever you specify `PRJ=☆`, the **Makefile** will be updated. If you're working on the same project, you can omit `PRJ=☆` in your `make` command.

----------

Submit Pull Requests!
=====================

This is a (potentially buggy) work in progress. Please submit **pull requests** if you have a fix to my code,  a different/better way of doing something, or a new feature you want other people to be able to use.

If you notice something is broken but you don't have a fix, would like a new feature, or want to have a discussion about doing something a different way (different directory structure, composition vs. inheritance for Chisel types, etc.), please create a new **issue**.

We'd like to hear from *you*, the users, about how we can increase your coding productivity, so comment away! :)

>**Note**: This setup currently doesn't support multi-project aggregates (designs consisting of multiple sub-projects stitched together via a top-level design specified by `PRJ=☆` in your Makefile). You can get a multi-project aggregate up and running by modifying  **ChiselEnvironment/ChiselProject/sbt/build.sbt** and adding the sub-project scala files to **ChiselEnvironment/ChiselProject/sbt/♢/src/main/scala**. Alternatively, set **ChiselEnvironment/ChiselProject/sbt/♢/src/main/scala** as a symbolic link to your sub-project's **ChiselEnvironment/ChiselPrj♢** directory. To add a sub-project **♢** (depending on sub-project **♧**) to your build.sbt, use `lazy val ♢ = yourSBTProject.dependsOn(♧).aggregate(♧)`. See [sbt docs](http://www.scala-sbt.org/0.13/docs/Multi-Project.html) for more information. In **build.sbt**, you will also have to modify the dependencies of the **root** project to include the additional sub-projects. When you have multiple main classes, sbt will prompt you to choose one to run.

----------

Check Out DSP
!
====================

Unfortunately, features I've built into DSP
 are not well documented outside of the code. **ChiselEnvironment/DSP
 ** is broken up into 2 sub-directories.

 - **Overlay**: Custom Chisel types specific to DSP
  and utility functions. Most of the custom types are modeled after pre-existing Chisel types. Therefore, the constructs are similar and most of the operators are retained (but there are more options!).
 - **Modules**: Useful *generalized* modules for DSP hardware generation.

>**Note**: Files in **ChiselEnvironment/ChiselCompatibility/** are only meant to remedy any compatibility issues between base Chisel and DSP
.

----------

DSP
 Types
====================

For more information, see [here](DSP
/Overlay/DSPTypes)) . DSP
 types are backwards compatible with their respective Chisel types (via implicit conversion), but Chisel types must be cast to their equivalent DSP
  types (since Chisel types have no knowledge of range, etc.).

 - **DSPBool**
 - **DSPUInt**
 - **DSPFixed**
 - **DSPSInt** (subset of *DSPFixed* with *fracWidth=0*)
 - **DSPDbl** (shares the same operators as *DSPFixed* so the two can be interchanged with *gen*)
 - **DSPComplex** (created via `Complex(real,imag)` where real and imag must both either be *DSPFixed* or *DSPDbl*)

> **Note #1**: Pipelining and registering are built into the type so that pipeline delays (specifically for a node y `val y = x.pipe(number_of_cycles)`) are tracked. Normal signal registering is untracked, but you can use `val y = x.reg()` for such an operation.
>
> **Note #2**: Another useful (new) operator is `x ? cond` which will return *x* if *cond* is true else 0. This was used to build the DSP
 **Mux**.

----------

A Note on Memories...
====================

For FPGA's, registering only the read address or only the read data out should allow you to pass behavioral simulation **but** if you try to synthesize with your memories as BRAM, post-synthesis functional simulation most likely fail. It seems to pass if you synthesize your memories into distributed RAM by (in *Vivado*) going to **Synthesis Settings**, **More Options** and using the directive `-ram_style distributed`. Note that distributed RAM can behave like a register file, where read can be performed asynchronously.

In general, however, it's best (safest) to register both the read address and read data out. When you instantiate your DSP
 **Memory**, use `seqRead = true, outReg = true`. **seqRead** determines whether to register the read address; **outReg** determines whether to register the data out. That way, post-synthesis functional simulation will also pass with BRAM.

> **Note**: In general, to force the type of RAM Xilinx should infer, use `-ram_style ☆` where ☆ is **block**, **auto**, or **distributed**. Check out [Dillon Engineering](http://www.dilloneng.com/inferring-block-ram-vs-distributed-ram-in-xst-and-precision.html#/) for more information on RAM inference.

For ASIC, currently the Memory wrapper only supports dual-ported memories, and because most SRAMs need to be initialized, the Chisel **reset** is used for the SRAM reset signal!

----------

Functional Programming 101
====================

Check out [Twitter's Scala School](https://twitter.github.io/scala_school/)!

----------


Clocking
====================

1. [ASIC clocking tutorial](http://ewh.ieee.org/soc/cas/dallas/documents/clock_balance_ieee_seminar04.pdf)

2. [Constraining your clocks](http://electronics.stackexchange.com/questions/83456/asic-timing-constraints-via-sdc-how-to-correctly-specify-a-ripple-divided-clock)

----------

Lessons Learned
====================

1. Don't only rely on tools to optimize your design. Sometimes, they just can't. Only you know what the optimal constraints for your design are.

2. FPGA's can't divide (by anything *except* powers of 2).

3. Document your design--especially your external interface (inputs, outputs, & timing, preferably with a timing diagram)--to ease validation and systems integration.

4. Post-synthesis Verilog verification is **extremely important**. Just because Chisel tests pass doesn't mean your design will work post-synthesis (after macros like SRAM, BRAM, distributed RAM, etc. have been swapped in).

5. FPGA's are terrible at retiming across muxes (i.e. when you want to support different rounding modes).

6. Be careful with nested conditions and **when** statements. Don't be sloppy and forget to specify signal output for every possible input condition!

----------

What Does X Error Message Mean?
====================

1. Previous lines of code have used L in L := R. To ensure range consistency, L cannot be updated with an R of wider range. Move := earlier in the code!
> This error usually pops up when you reassign to a signal node. The node was already used as the input to some other operation, and the operation relied on known input ranges to infer output bitwidths & ranges. If you reassign a signal with a larger possible range to this node, the range inference previously performed will be wrong. **In general, avoid signal reassignment** where possible. Obviously, this will not work when you have feedback (simplest example is a counter). To get around this limitation, you need to explicitly specify the range of R to be equal to the range of L. Use `L := R.shorten(L.getRange)` for the types: **DSPUInt, DSPSInt, DSPFixed, gen**.

To be continued... 

----------

This code is maintained by [Angie](https://github.com/shunshou) and [Paul](https://github.com/grebe). Let us know if you have any questions/feedback!

Copyright (c) 2015 - 2016 The Regents of the University of California. Released under the Modified (3-clause) BSD license.
