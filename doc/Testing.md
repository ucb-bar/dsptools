# Details on DspTesterOptionsManager
* As in the example above, the DspTesterOptionsManager manages the following category of options listed hierarchically [objectName = caseClass] (Case class files linked):
  * dspTesterOptions = [DspTesterOptions()](https://github.com/ucb-bar/dsptools/blob/add_ops/src/main/scala/dsptools/tester/DspTesterOptions.scala)
  * testerOptions = [TesterOptions()](https://github.com/ucb-bar/chisel-testers/blob/master/src/main/scala/chisel3/iotesters/TesterOptions.scala)
 * interpreterOptions = [InterpreterOptions()](https://github.com/ucb-bar/firrtl-interpreter/blob/master/src/main/scala/firrtl_interpreter/Driver.scala)
  * treadleOptions = [TreadleOptions()](https://github.com/freechipsproject/treadle/blob/master/src/main/scala/treadle/Driver.scala)
  * chiselOptions = [ChiselExecutionOptions()](https://github.com/ucb-bar/chisel3/blob/master/src/main/scala/chisel3/ChiselExecutionOptions.scala)
  * firrtlOptions = [FirrtlExecutionOptions()](https://github.com/ucb-bar/firrtl/blob/master/src/main/scala/firrtl/ExecutionOptionsManager.scala)
  * commonOptions = [CommonOptions](https://github.com/ucb-bar/firrtl/blob/master/src/main/scala/firrtl/ExecutionOptionsManager.scala)
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
      * clock periods for initial reset (for use with TB generation)
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
