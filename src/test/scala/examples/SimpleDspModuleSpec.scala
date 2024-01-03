// SPDX-License-Identifier: Apache-2.0

package examples

// Allows you to use Chisel Module, Bundle, etc.
import chisel3._
import dsptools.misc.PeekPokeDspExtensions
// Allows you to use FixedPoint
import fixedpoint._
// If you want to take advantage of type classes >> Data:RealBits (i.e. pass in FixedPoint or DspReal)
import dsptools.numbers._
// Enables you to set DspContext's for things like overflow behavior, rounding modes, etc.
import dsptools.DspContext
// Use chiseltest
import chiseltest._
// Use chiseltest's iotesters inferface
import chiseltest.iotesters._
// Scala unit testing style
import org.scalatest.flatspec.AnyFlatSpec

// IO Bundle. This also creates x, y, z inputs/outputs (direction must be specified at some IO hierarchy level)
// of the type you specify via gen (must be Data:RealBits = UInt, SInt, FixedPoint, DspReal)
class SimpleDspIo[T <: Data: RealBits](gen: T) extends Bundle {
  val x = Input(gen.cloneType)
  val y = Input(gen.cloneType)
  val z = Output(gen.cloneType)
}

// Parameterized Chisel Module; takes in type parameters as explained above
class SimpleDspModule[T <: Data: RealBits](gen: T, val addPipes: Int) extends Module {
  // This is how you declare an IO with parameters
  val io = IO(new SimpleDspIo(gen))
  // Output will be current x + y addPipes clk cycles later
  // Note that this relies on the fact that type classes have a special + that
  // add addPipes # of ShiftRegister after the sum. If you don't wrap the sum in
  // DspContext.withNumAddPipes(addPipes), the default # of addPipes is used.
  DspContext.withNumAddPipes(addPipes) {
    io.z := io.x.context_+(io.y)
  }
}

// You create a tester that must extend PeekPokeDspExtensions to support Dsp type peeks/pokes (with doubles, complex, etc.)
class SimpleDspModuleTester[T <: Data: RealBits](c: SimpleDspModule[T])
    extends PeekPokeTester(c)
    with PeekPokeDspExtensions {
  val x = Seq(-1.1, -0.4, 0.4, 1.1)
  val z = x.map(2 * _)
  for (i <- 0 until (x.length + c.addPipes)) {
    val in = x(i % x.length)
    // Feed in to the x, y inputs
    poke(c.io.x, in)
    poke(c.io.y, in)

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
class SimpleDspModuleSpec extends AnyFlatSpec with ChiselScalatestTester {

  behavior.of("simple dsp module")

  it should "properly add fixed point types" ignore {
    // Run the dsp tester by following this style: You need to pass in the module to test. Note that here, you're
    // testing the module with inputs/outputs of FixedPoint type (Q15.12) and 3 registers (for retiming) at the output.
    // You could alternatively use DspReal()
    // Scala keeps track of which tests pass/fail.
    // Supposedly, Chisel3 testing infrastructure might be overhauled to reduce the amount of boilerplate,
    // but this is currently the endorsed way to do things.
    test(new SimpleDspModule(FixedPoint(16.W, 12.BP), addPipes = 3))
      .runPeekPoke(new SimpleDspModuleTester(_))
  }

}
