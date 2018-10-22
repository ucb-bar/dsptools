package dsptools.numbers.representations
import org.scalatest.{FlatSpec, Matchers}
import chisel3._
import generatortools.io.CustomBundle
import chisel3.experimental._
import chisel3.util._
import chisel3.internal.firrtl.IntervalRange

class MixedRadixCounter(
    maxRadicesHighFirst: Seq[Int],            // Maximum supported radices for each digit
    autoWrap: Boolean,                        // Wrap on overflow (vs. equal to a counter maximum)
    constantMax: Seq[Int],                    // Use as max for counter wrap condition if non-empty
    constantRads: Boolean,                    // If true, counter radices = maxRadicesHighFirst (not reconfigurable)
    constantInc: Seq[Int],                    // If non-empty, constant increment value in mixed-radix (requires constantRads)
    constantInit: Seq[Int],                   // If non-empty, constant initialization value in mixed-radix (requires constantRads)
    extraCarryIn: Boolean) extends Module {   // Increments by (inc + 1) if true  

  if (constantInc.nonEmpty) require(constantRads, "Constant increment value requires constant radices")
  if (constantInit.nonEmpty) require(constantRads, "Constant initialization value requires constant radices")

  // TODO: Add Option instead of just selectively use -- check @ runtime

  val io = new Bundle {
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
    val wrap = Output(Bool())
  }

  val radicesHighFirst = 
    if (constantRads) maxRadicesHighFirst.map { case rad => rad.U }
    else io.radicesHighFirst.seq 

  val inc = 
    if (constantInc.nonEmpty) MixedRadix.wire(constantInc.map { case i => i.U }, radicesHighFirst)
    else MixedRadix.wire(io.inc.seq, radicesHighFirst)

  val init = 
    if (constantInit.nonEmpty) MixedRadix.wire(constantInit.map { case i => i.U }, radicesHighFirst)
    else MixedRadix.wire(io.init.seq, radicesHighFirst)

  val count = Wire(new MixedRadix(io.out.seq, radicesHighFirst))
  count := init

  val countNext = count.add(inc, extraCarryIn.B)

  val zero = MixedRadix.wire(Seq.fill(maxRadicesHighFirst.length)(0.U), radicesHighFirst)

  val max = 
    if (constantMax.nonEmpty) CustomBundle.wire(constantMax.map { case i => i.U } )
    else io.max

  // Reset is not asynchronous (init value is assigned on next clock cycle)
  withReset(io.reset) { 
    val countNextWithWrap =
      if (autoWrap) {
        // Note that this uses the future counter value
        io.wrap := CustomBundle.eq(countNext.digits, zero.digits) && io.enable
        countNext
      }
      else {
        // Whereas this uses the current counter value
        val maxedOut = CustomBundle.eq(count.digits, max)
        // Wrap means that at the next clock cycle, the counter will have wrapped (so factor in enable)
        io.wrap := maxedOut && io.enable
        Mux(maxedOut, zero, countNext)
      }
    count := RegEnable(countNextWithWrap, init = init, enable = io.enable)
  }
  io.out := count
}

//////////////////////////////////////////////////////////

object MixedRadixCounter {
  /** Mixed radix counter with configurable radix and max amount (for wrap). Increments by 1 each
    * clock cycle if enabled and not reset (to 0).
    */
  def inc1WithRadAndMaxConfig(maxRadicesHighFirst: Seq[Int]): MixedRadixCounter = {
    Module(new MixedRadixCounter(
      maxRadicesHighFirst = maxRadicesHighFirst,
      autoWrap = false,
      constantMax = Seq.empty,
      constantRads = false,
      constantInc = Seq.fill(maxRadicesHighFirst.length)(0),
      constantInit = Seq.fill(maxRadicesHighFirst.length)(0),
      extraCarryIn = true
    ))
  }
  /** Mixed radix counter with configurable radix. Auto wraps; increments by 1 each
    * clock cycle if enabled and not reset (to 0). 
    */
  def inc1AutoWrapWithRadConfig(maxRadicesHighFirst: Seq[Int]): MixedRadixCounter = {
    Module(new MixedRadixCounter(
      maxRadicesHighFirst = maxRadicesHighFirst,
      autoWrap = true,                                        // Modulo number represented by radices
      constantMax = Seq.empty,
      constantRads = false,
      constantInc = Seq.fill(maxRadicesHighFirst.length)(0),
      constantInit = Seq.fill(maxRadicesHighFirst.length)(0),
      extraCarryIn = true
    ))
  }

  /** Mixed radix counter with configurable radix. Auto wraps; increments by specified amount each
    * clock cycle if enabled and not reset (to 0). 
    */
  def autoWrapWithIncAndRadConfig(maxRadicesHighFirst: Seq[Int]): MixedRadixCounter = {
    Module(new MixedRadixCounter(
      maxRadicesHighFirst = maxRadicesHighFirst,
      autoWrap = true,                                        // Modulo number represented by radices
      constantMax = Seq.empty,
      constantRads = false,
      constantInc = Seq.empty,
      constantInit = Seq.fill(maxRadicesHighFirst.length)(0),
      extraCarryIn = false
    ))
  }
}