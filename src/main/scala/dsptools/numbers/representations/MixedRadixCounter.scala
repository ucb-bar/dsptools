package dsptools.numbers.representations
import org.scalatest.{FlatSpec, Matchers}
import chisel3._
import generatortools.io.CustomBundle
import chisel3.experimental._
import chisel3.util._
import chisel3.internal.firrtl.IntervalRange
import dsptools.{DspTester, DspTesterOptionsManager}
import iotesters.TesterOptions

@chiselName
class MixedRadixCounter(
    val maxRadicesHighFirst: Seq[Int],            // Maximum supported radices for each digit
    val autoWrap: Boolean,                        // Wrap on overflow (vs. equal to a counter maximum)
    val constantMax: Seq[Int],                    // Use as max for counter wrap condition if non-empty
    val constantRads: Boolean,                    // If true, counter radices = maxRadicesHighFirst (not reconfigurable)
    val constantInc: Seq[Int],                    // If non-empty, constant increment value in mixed-radix (requires constantRads)
    val constantInit: Seq[Int],                   // If non-empty, constant initialization value in mixed-radix (requires constantRads)
    val extraCarryIn: Boolean) extends Module {   // Increments by (inc + 1) if true  

  if (constantInc.nonEmpty) 
    if (constantInc != Seq.fill(constantInc.length)(0))
      require(constantRads, "Constant increment value requires constant radices")
  if (constantInit.nonEmpty) 
    if (constantInit != Seq.fill(constantInit.length)(0))
      require(constantRads, "Constant initialization value requires constant radices")

  maxRadicesHighFirst foreach { case rad => require(rad > 0, "Radix must be > 0") }

  // TODO: Add Option instead of just selectively use -- check @ runtime

  val io = IO(new Bundle {
    val reset = Input(Bool())
    val enable = Input(Bool())
    // Only used if radices aren't constant
    val radicesHighFirst = Input(CustomBundle(maxRadicesHighFirst.map { case rad => UInt(range"[0, $rad]") } ))
    // Only used if not a constant increment counter
    // Note that digits can be max corresponding radix - 1
    val inc = Input(CustomBundle(maxRadicesHighFirst.map { case rad => UInt(range"[0, $rad)") } ))
    // Only used if counter isn't supposed to auto-wrap on overflow and constantMax is empty
    val max = Input(inc.cloneType)
    // Only used if init isn't constant
    val init = Input(inc.cloneType)
    val out = Output(inc.cloneType)
    // Doesn't include enable (just when counter is maxed)
    val isMax = Output(Bool())
  } )

  // TODO: Anywhere to eliminate MixedRadix.wire?

  val radicesHighFirst = 
    if (constantRads) maxRadicesHighFirst.map { case rad => rad.U }
    else io.radicesHighFirst.seq 

  val inc = 
    if (constantInc.nonEmpty) MixedRadix.wire(constantInc.map { case i => i.U }, radicesHighFirst)
    else MixedRadix.wire(io.inc.seq, radicesHighFirst)

  val init = 
    if (constantInit.nonEmpty) MixedRadix.wire(
      // TODO: Don't have Reg size be dependent on init
      constantInit.zip(maxRadicesHighFirst).map { case (i, rad) => i.U((BigInt(rad - 1).bitLength).W) }, 
      radicesHighFirst
    )
    else MixedRadix.wire(io.init.seq, radicesHighFirst)

  val count = Wire(new MixedRadix(io.out.seq, radicesHighFirst))
  count := init

  val countNext = count.add(inc, extraCarryIn.B)

  val zero = MixedRadix.wire(Seq.fill(maxRadicesHighFirst.length)(0.U), radicesHighFirst)

  val max = 
    if (constantMax.nonEmpty) CustomBundle.wire(constantMax.map { case i => i.U } )
    else io.max

  val countNextWithWrap =
    if (autoWrap) {
      // Note that this uses the future counter value
      io.isMax := CustomBundle.eq(countNext.digits, zero.digits) // && io.enable
      countNext
    }
    else {
      // Whereas this uses the current counter value
      val maxedOut = CustomBundle.eq(count.digits, max)
      // Wrap means that at the next clock cycle, the counter will have wrapped (so factor in enable)
      io.isMax := maxedOut // && io.enable

      // TODO: Figure out why mux doesn't work
      // Mux(maxedOut, zero, countNext)
      val countNextWithWrapInt = Wire(countNext.cloneType)
      countNextWithWrapInt.radicesHighFirst := countNext.radicesHighFirst
      (0 until countNext.digits.seq.length).foreach { case idx =>
        countNextWithWrapInt.digits(idx) := Mux(maxedOut, zero.digits(idx), countNext.digits(idx))
      }
      countNextWithWrapInt
    }

  // Reset is not asynchronous (init value is assigned on next clock cycle)
  withReset(io.reset) { 
    count := RegEnable(countNextWithWrap, init = init, enable = io.enable)
  }
  io.out := count.digits
}

//////////////////////////////////////////////////////////

object MixedRadixCounter {
  /** Mixed radix counter with configurable radix and max amount (for wrap). Increments by 1 each
    * clock cycle if enabled and not reset (to 0).
    */
  def inc1WithRadAndMaxConfig(maxRadicesHighFirst: Seq[Int]): MixedRadixCounter = {
    new MixedRadixCounter(
      maxRadicesHighFirst = maxRadicesHighFirst,
      autoWrap = false,
      constantMax = Seq.empty,
      constantRads = false,
      constantInc = Seq.fill(maxRadicesHighFirst.length)(0),
      constantInit = Seq.fill(maxRadicesHighFirst.length)(0),
      extraCarryIn = true
    )
  }
  /** Mixed radix counter with configurable radix. Auto wraps; increments by 1 each
    * clock cycle if enabled and not reset (to 0). 
    */
  def inc1AutoWrapWithRadConfig(maxRadicesHighFirst: Seq[Int]): MixedRadixCounter = {
    new MixedRadixCounter(
      maxRadicesHighFirst = maxRadicesHighFirst,
      autoWrap = true,                                        // Modulo number represented by radices
      constantMax = Seq.empty,
      constantRads = false,
      constantInc = Seq.fill(maxRadicesHighFirst.length)(0),
      constantInit = Seq.fill(maxRadicesHighFirst.length)(0),
      extraCarryIn = true
    )
  }

  /** Mixed radix counter with configurable radix. Auto wraps; increments by specified amount each
    * clock cycle if enabled and not reset (to 0). 
    */
  def autoWrapWithIncAndRadConfig(maxRadicesHighFirst: Seq[Int]): MixedRadixCounter = {
    new MixedRadixCounter(
      maxRadicesHighFirst = maxRadicesHighFirst,
      autoWrap = true,                                        // Modulo number represented by radices
      constantMax = Seq.empty,
      constantRads = false,
      constantInc = Seq.empty,
      constantInit = Seq.fill(maxRadicesHighFirst.length)(0),
      extraCarryIn = false
    )
  }
}

//////////////////////////////////////////////////////////


class MixedRadixCounterSpec extends FlatSpec with Matchers {

  val maxRadicesHighFirst = Seq(5, 5, 5, 5, 5)

  behavior of "+1 counter with configurable radix + wrap max config"

  it should "properly count" in {
    dsptools.Driver.execute(() => MixedRadixCounter.inc1WithRadAndMaxConfig(maxRadicesHighFirst)) { c =>
        new MixedRadixCounterTester(c)
    } should be (true)
  }

  behavior of "+1 counter with configurable radix + auto wrap config"

  it should "properly count" in {
    dsptools.Driver.execute(() => MixedRadixCounter.inc1AutoWrapWithRadConfig(maxRadicesHighFirst)) { c =>
        new MixedRadixCounterTester(c)
    } should be (true)
  }

  behavior of "+x counter with configurable radix + auto wrap config"

  it should "properly count" in {
    dsptools.Driver.execute(() => MixedRadixCounter.autoWrapWithIncAndRadConfig(maxRadicesHighFirst)) { c =>
        new MixedRadixCounterTester(c)
    } should be (true)
  }

}

//////////////////////////////////////////////////////////

class MixedRadixCounterTester(c: MixedRadixCounter) extends DspTester(c) {
  // Poke base 10 number as mixed radix number
  def pokeMixedRadix(signal: CustomBundle[UInt], n: Int, radsHighFirst: Seq[Int]) = {
    val digits = MixedRadix.toPaddedDigitSeqMSDFirst(n, radsHighFirst)
    digits.zipWithIndex foreach { case (digit, idx) => poke(signal(idx), digit) }
  }
  def expectMixedRadix(signal: CustomBundle[UInt], n: Int, radsHighFirst: Seq[Int]) = {
    val digits = MixedRadix.toPaddedDigitSeqMSDFirst(n, radsHighFirst)
    digits.zipWithIndex foreach { case (digit, idx) => expect(signal(idx), digit) }
  }
  def peekMixedRadix(signal: CustomBundle[UInt], radsHighFirst: Seq[Int]) = {
    radsHighFirst.zipWithIndex foreach { case (rad, idx) => peek(signal(idx)) }
  }
  val rads = Seq(5, 5, 1, 4, 3)
  val inc = 2
  val max = rads.product - 10
  // c.io.init always zero in useful counters
  // rads > maximum representable #
  rads.zipWithIndex foreach { case (rad, idx) => poke(c.io.radicesHighFirst(idx), rad) }
  pokeMixedRadix(c.io.inc, inc, rads)
  pokeMixedRadix(c.io.max, max, rads)
  poke(c.io.enable, true)
  poke(c.io.reset, true)
  step(1)
  poke(c.io.reset, false)

  val testIdxs = (0 until rads.product * 3)
  val countsWithoutWrap = 
    // + inc amount
    if (c.constantInc.isEmpty) testIdxs.map(_ * inc)
    // + 1
    else testIdxs
  val countsTemp = 
    // Wrap @ max counter amount
    if (c.autoWrap) countsWithoutWrap.map(_ % rads.product)  
    // Max is achievable so wrap on max + 1
    else countsWithoutWrap.map(_ % (max + 1))
  val wraps = 
    // Auto wrap wraps when *next* counter value is zero
    if (c.autoWrap) countsTemp.map(_ == 0).tail
    // Otherwise, wraps on user-specified max
    else countsTemp.map(_ == max).init
  val counts = countsTemp.init

  counts.zip(wraps) foreach { case (count, wrap) =>
    expectMixedRadix(c.io.out, count, rads)
    expect(c.io.isMax, wrap)
    step(1)
  }
 
}
