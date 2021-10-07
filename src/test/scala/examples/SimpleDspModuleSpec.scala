// SPDX-License-Identifier: Apache-2.0

package SimpleDsp

// Allows you to use Chisel Module, Bundle, etc.
import chisel3._
// Allows you to use FixedPoint
import chisel3.experimental.FixedPoint
// If you want to take advantage of type classes >> Data:RealBits (i.e. pass in FixedPoint or DspReal)
import dsptools.numbers._
// Enables you to set DspContext's for things like overflow behavior, rounding modes, etc.
import dsptools.DspContext
// Use DspTester, specify options for testing (i.e. expect tolerances on fixed point, etc.)
import dsptools.{DspTester, DspTesterOptionsManager, DspTesterOptions}
// Allows you to modify default Chisel tester behavior (note that DspTester is a special version of Chisel tester)
import iotesters.TesterOptions
// Scala unit testing style
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// IO Bundle. Note that when you parameterize the bundle, you MUST override cloneType.
// This also creates x, y, z inputs/outputs (direction must be specified at some IO hierarchy level)
// of the type you specify via gen (must be Data:RealBits = UInt, SInt, FixedPoint, DspReal)
class SimpleDspIo[T <: Data:RealBits](gen: T) extends Bundle {
  val x = Input(gen.cloneType)
  val y = Input(gen.cloneType)
  val z = Output(gen.cloneType)
}

// Parameterized Chisel Module; takes in type parameters as explained above
class SimpleDspModule[T <: Data:RealBits](gen: T, val addPipes: Int) extends Module {
  // This is how you declare an IO with parameters
  val io = IO(new SimpleDspIo(gen))
  // Output will be current x + y addPipes clk cycles later
  // Note that this relies on the fact that type classes have a special + that
  // add addPipes # of ShiftRegister after the sum. If you don't wrap the sum in
  // DspContext.withNumAddPipes(addPipes), the default # of addPipes is used.
  DspContext.withNumAddPipes(addPipes) {
    io.z := io.x context_+ io.y
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
class SimpleDspModuleSpec extends AnyFlatSpec with Matchers {

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
    // Run the dsp tester by following this style: You need to pass in the module to test and your created DspTesterOptionsManager.
    // Note that here, you're testing the module with inputs/outputs of FixedPoint type (Q15.12) and 3 registers (for retiming) at the output.
    // You could alternatively use DspReal()
    // Scala keeps track of which tests pass/fail.
    // Supposedly, Chisel3 testing infrastructure might be overhauled to reduce the amount of boilerplate, but this is currently
    // the endorsed way to do things.
    dsptools.Driver.execute(() => new SimpleDspModule(FixedPoint(16.W, 12.BP), addPipes = 3), testOptions) { c =>
      new SimpleDspModuleTester(c)
    } should be (true)
  }

}
