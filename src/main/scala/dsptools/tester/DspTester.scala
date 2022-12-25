// SPDX-License-Identifier: Apache-2.0

package dsptools

import breeze.math.Complex
import chisel3._
import chisel3.experimental.FixedPoint
import chisel3.internal.firrtl.KnownBinaryPoint
import chisel3.iotesters.{PeekPokeTester, Pokeable}
import dsptools.DspTesterUtilities._
import dsptools.numbers.{DspComplex, DspReal}

import scala.util.DynamicVariable

// TODO: Get rid of
import chisel3.iotesters.TestersCompatibility

//scalastyle:off number.of.methods cyclomatic.complexity
class DspTester[+T <: MultiIOModule](
    dut: T,
    base: Int = 16,
    logFile: Option[java.io.File] = None) extends PeekPokeTester(dut, base, logFile) with VerilogTbDump {

  // Changes displayed base of standard PeekPokeTester info text
  // Note: base is not relevant to Doubles
  val updatableBase = new DynamicVariable[Int](_base)
  private def dispBase = updatableBase.value

  // Verbosity @ highest DSP level (different from chisel-testers verbosity = standard peek/poke)
  val updatableDspVerbose = new DynamicVariable[Boolean](dsptestersOpt.isVerbose)
  private def dispDsp = updatableDspVerbose.value

  // Verbosity @ normal peek/poke level (chisel-testers verbosity) -- for additionally displaying as bits
  // and also all sub levels of abstraction i.e. expect relies on peek, but peek string won't be printed
  // if dispSub = false
  val updatableSubVerbose = new DynamicVariable[Boolean](iotestersOM.testerOptions.isVerbose)
  private def dispSub = updatableSubVerbose.value

  // UInt, SInt, FixedPoint tolerance in LSBs
  val fixTolLSBs = new DynamicVariable[Int](dsptestersOpt.fixTolLSBs)
  private def fixTol = fixTolLSBs.value

  // DspReal tolerance in decimal point precision
  val realTolDecPts = new DynamicVariable[Int](dsptestersOpt.realTolDecPts)
  private def realTol = realTolDecPts.value

  // Tester starts with reset (but don't count cycles during reset)
  //scalastyle:off magic.number
  super.reset(5)

  ///////////////// OVERRIDE UNDERLYING FUNCTIONS FROM PEEK POKE TESTER /////////////////

  override def step(n: Int) {
    if (dispDsp) logger info s"STEP ${n}x -> ${t + n}"
    stepPrint(n)
    backend.step(n)
    incTime(n)
  }

  override def reset(n: Int = 1) {
    if (dispDsp) logger info s"RESET ${n}x -> ${t + n}"
    resetPrint(n)
    backend.reset(n)
    incTime(n)
  }

  private def maskedBigInt(bigInt: BigInt, width: Int): BigInt = {
    val maskedBigInt = bigInt & ((BigInt(1) << width) - 1)
    maskedBigInt
  }

  override def poke(path: String, value: BigInt): Unit = {
    if (verilogTb) throw DspException("Don't use poke path (String) when printing verilog tb")
    backend.poke(path, value)(logger, dispDsp, dispBase)
  }

  override def poke[T <: Element : Pokeable](signal: T, value: BigInt): Unit = {
    // bit-level poke is displayed as unsigned
    val maskedValue = maskedBigInt(value, signal.widthOption.getOrElse(128))
    validRangeTest(signal, value)
    if (!signal.isLit) backend.poke(signal, maskedValue, None)(logger, dispDsp, dispBase)
    pokePrint(signal, value)
  }

  // Poke at does not involve external signals -- no VerilogTB print
  override def pokeAt[TT <: Element : Pokeable](data: MemBase[TT], value: BigInt, off: Int): Unit = {
    backend.poke(data, value, Some(off))(logger, dispDsp, dispBase)
  }

  override def peek(path: String): BigInt = {
    if (verilogTb) throw DspException("Don't use peek path (String) when printing verilog tb")
    backend.peek(path)(logger, dispDsp, dispBase)
  }

  override def peek[T <: Element : Pokeable](signal: T): BigInt = {
    val o = {
      // bit-level peek is displayed as unsigned
      if (!signal.isLit) {
        backend.peek(signal, None)(logger, dispDsp, dispBase)
      } else {
        val litVal = signal.litValue()
        if (dispDsp) {
          logger info s"  PEEK ${getName(signal)} -> ${TestersCompatibility.bigIntToStr(litVal, dispBase)}"
        }
        litVal
      }
    }
    peekPrint(signal, o)
    o
  }

  override def peek(signal: Aggregate): IndexedSeq[BigInt] =  {
    // Flatten returns IndexSeq[BigInt], so will use above peek
    TestersCompatibility.flatten(signal) map { x => peek(x) }
  }

  // Peek at does not involve external signals -- no VerilogTB print
  override def peekAt[TT <: Element : Pokeable](data: MemBase[TT], off: Int): BigInt = {
    backend.peek(data, Some(off))(logger, dispDsp, dispBase)
  }

  override def expect(good: Boolean, msg: => String): Boolean = {
    if (dispDsp || !good) logger info ( { if (!good) Console.RED else "" } +
      s"EXPECT AT $t $msg ${if (good) "PASS" else "FAIL"}" + Console.RESET)
    if (!good) fail
    good
  }

  def expect(signal: Bits, expected: BigInt): Boolean = expect(signal, expected, "")
  override def expect[T <: Element : Pokeable](signal: T, expected: BigInt, msg: => String): Boolean = {
    validRangeTest(signal, expected)
    val path = getName(signal)
    val got = updatableDspVerbose.withValue(dispSub) { peek(signal) }
    val good = got == expected
    if (dispDsp || !good) logger info ( { if (!good) Console.RED else "" } +
      s"$msg  EXPECT $path -> ${TestersCompatibility.bigIntToStr(got, dispBase)} == E " +
        s"${TestersCompatibility.bigIntToStr(expected, dispBase)} ${if (good) "PASS" else "FAIL"}" + Console.RESET)
    if (!good) fail
    good
  }

  override def finish: Boolean = {
    finishVerilogTb()
    super.finish
  }

  ///////////////// UNDERLYING FUNCTIONS FROM DSP TESTER /////////////////

  def poke(signal: Bool, value: Boolean): Unit = poke(signal, if (value) BigInt(1) else BigInt(0))

  // Need to specify what happens when you poke with an Int
  // Display as Double (so you can separate out number representation vs. bit representation)
  def poke(signal: UInt, value: Int): Unit = poke(signal.asInstanceOf[Data], value.toDouble)
  def poke(signal: SInt, value: Int): Unit = poke(signal.asInstanceOf[Data], value.toDouble)

  // Has priority over Bits (FixedPoint extends Bits)
  def poke(signal: FixedPoint, value: Int): Unit = poke(signal, value.toDouble)
  def poke(signal: FixedPoint, value: Double): Unit = poke(signal.asInstanceOf[Data], value)

  // DspReal extends Bundle extends Aggregate extends Data
  // If poking DspReal with Double, can only go here
  // Type classes are all Data:RealBits
  //scalastyle:off cyclomatic.complexity
  def poke(signal: Data, value: Double): Unit = {
    updatableDspVerbose.withValue(dispSub) {
      signal match {
        case f: FixedPoint =>
          f.binaryPoint match {
            case KnownBinaryPoint(bp) => poke(f.asInstanceOf[Bits], FixedPoint.toBigInt(value, bp))
            case _ => throw DspException("Must poke FixedPoint with known binary point")
          }
        case r: DspReal => poke(r.node.asInstanceOf[Bits], DspTesterUtilities.doubleToBigIntBits(value))
        // UInt + SInt
        case b: Bits => poke(b.asInstanceOf[Bits], BigInt(value.round.toInt))
        case _ => throw DspException("Illegal poke value for node of type Data and value of type Double")
      }
    }
    if (dispDsp) logger info s"  POKE ${getName(signal)} <- $value, ${bitInfo(signal)}"
  }
  def poke(signal: Data, value: BigDecimal): Unit = {
    assert(value <= Double.MaxValue, s"poking ${signal} with a value $value bigger than Double.MaxValue")
    poke(signal, value.toDouble)
  }

  // Will only print individual real/imag peek information if dispSub is true!
  def poke(c: DspComplex[_], value: Complex): Unit = {
    updatableDspVerbose.withValue(dispSub) {
      poke(c.real.asInstanceOf[Data], value.real)
      poke(c.imag.asInstanceOf[Data], value.imag)
    }
    if (dispDsp) logger info s"  POKE ${getName(c)} <- ${value.toString}, ${bitInfo(c)}"
  }

  def dspPeekWithBigInt(node: Data): (Double, BigInt) = dspPeek(node)

  private def dspPeek(node: Data): (Double, BigInt) = {
    val bi: BigInt = updatableDspVerbose.withValue(dispSub) {
      node match {
        // Unsigned bigint
        case r: DspReal => peek(r.node.asInstanceOf[Bits])
        case b: Bits => peek(b.asInstanceOf[Bits])
      }
    }
    val (dblOut, bigIntOut) = node match {
      case _: DspReal => (DspTesterUtilities.bigIntBitsToDouble(bi), bi)
      case f: FixedPoint => f.binaryPoint match {
        case KnownBinaryPoint(bp) => (FixedPoint.toDouble(bi, bp), bi)
        case _ => throw DspException("Cannot peek FixedPoint with unknown binary point location")
      }
      // UInt + SInt = Bits
      case _: Bits => (bi.doubleValue, bi)
      case _ => throw DspException(s"Peeked node ${getName(node)} has incorrect type ${node.getClass.getName}")
    }
    if (dispDsp) logger info s"  PEEK ${getName(node)} -> $dblOut, ${bitInfo(node)}"
    (dblOut, bigIntOut)
  }

  // Takes precedence over Bits
  def peek(node: Bool): Boolean = {
    if (peek(node.asInstanceOf[Bits]) == 1) true else false
  }
  def peek(node: UInt): Int = dspPeek(node)._1.round.toInt
  def peek(node: SInt): Int = dspPeek(node)._1.round.toInt
  def peek(node: FixedPoint): Double = dspPeek(node)._1
  // Takes precedence over Aggregate
  def peek(node: DspReal): Double = dspPeek(node)._1

  // General type returns Double
  def peek(node: Data): Double = dspPeek(node)._1

  def peek(c: DspComplex[_]): Complex = {
    val out = updatableDspVerbose.withValue(dispSub) {
      Complex(dspPeek(c.real.asInstanceOf[Data])._1, dspPeek(c.imag.asInstanceOf[Data])._1)
    }
    if (dispDsp) logger info s"  PEEK ${getName(c)} <- ${out.toString}, ${bitInfo(c)}"
    out
  }

  def expect(signal: Bool, expected: Boolean): Boolean = expect(signal, expected, "")
  def expect(signal: Bool, expected: Boolean, msg: String): Boolean = {
    expect(signal.asInstanceOf[Bits], if (expected) BigInt(1) else BigInt(0), msg)
  }

  // If expecting directly on UInt or SInt (rather than generic type class),
  // set tolerance to be 0 bits (i.e. must match exactly)
  def expect(signal: UInt, expected: Int): Boolean = expect(signal, expected, "")
  def expect(signal: UInt, expected: Int, msg: String): Boolean = fixTolLSBs.withValue(0) {
    expect(signal.asInstanceOf[Data], expected.toDouble, msg)
  }
  def expect(signal: SInt, expected: Int): Boolean = expect(signal, expected, "")
  def expect(signal: SInt, expected: Int, msg: String): Boolean = fixTolLSBs.withValue(0) {
    expect(signal.asInstanceOf[Data], expected.toDouble, msg)
  }

  // Priority over Bits
  def expect(signal: FixedPoint, expected: Int): Boolean = expect(signal, expected, "")
  def expect(signal: FixedPoint, expected: Int, msg: String): Boolean = expect(signal, expected.toDouble, msg)
  def expect(signal: FixedPoint, expected: Double): Boolean = expect(signal, expected, "")
  def expect(signal: FixedPoint, expected: Double, msg: String): Boolean = {
    expect(signal.asInstanceOf[Data], expected, msg)
  }
  ///////////////// SPECIALIZED DSP EXPECT /////////////////

  //scalastyle:off cyclomatic.complexity
  def checkDecimal(data: Data, expected: Double, dblVal: Double, bitVal: BigInt): (Boolean, Double) = {
    def toMax(w: Int): BigInt = (BigInt(1) << w) - 1
    // <=
    val fixTolInt = toMax(fixTol)
    val floTolDec = math.pow(10,-realTol)
    // Error checking does a bad job of handling really small numbers,
    // so let's just force the really small numbers to 0
    val expected0 = if (math.abs(expected) < floTolDec/100) 0.0 else expected
    val dblVal0 = if (math.abs(dblVal) < floTolDec/100) 0.0 else dblVal
    val expectedBits = data match {
      case _: DspReal => DspTesterUtilities.doubleToBigIntBits(expected0)     // unsigned BigInt
      case f: FixedPoint => f.binaryPoint match {
        case KnownBinaryPoint(bp) => FixedPoint.toBigInt(expected0, bp)
        case _ => throw DspException("Unknown binary point in FixedPoint on expect")
      }
      case _: Bits => BigInt(expected0.round.toInt)
    }

    validRangeTest(data, expectedBits)

    // Allow for some tolerance in error checking
    val (tolerance, tolDec) = data match {
      case f: FixedPoint => f.binaryPoint match {
        case KnownBinaryPoint(bp) => (fixTolInt, FixedPoint.toDouble(fixTolInt, bp))
        case _ => throw DspException("Unknown binary point!")
      }
      case _: SInt | _: UInt => (fixTolInt, fixTolInt.toDouble)
      case _: DspReal => (DspTesterUtilities.doubleToBigIntBits(floTolDec), floTolDec)
    }
    val good = {
      if (dblVal0 != expected0) {
        val gotDiffDbl = math.abs(dblVal0-expected0)
        val gotDiffBits = (bitVal - expectedBits).abs
        val passDbl = gotDiffDbl <= tolDec
        val passBits = gotDiffBits <= tolerance
        passDbl && passBits
      } else {
        true
      }
    }
    (good, tolDec)
  }

  // Expect on DspReal goes straight to here
  def expect(data: Data, expected: Double): Boolean = expect(data, expected, msg = "")
  def expectWithoutFailure(data: Data, expected: Double, msg: String = ""): Boolean = {
    val expectedNew = roundData(data, expected)
    val path = getName(data)
    val (dblVal, bitVal) = updatableDspVerbose.withValue(dispSub) { dspPeek(data) }
    val (good, tolerance) = checkDecimal(data, expectedNew, dblVal, bitVal)
    if (dispDsp) logger info
      (
        s"$msg  EXPECT $path -> $dblVal == E " +
        s"$expectedNew ${if (good) "PASS" else "FAIL"}, tolerance = $tolerance, ${bitInfo(data)}" +
        Console.RESET
      )
    good
  }
  def expect(data: Data, expected: Double, msg: String): Boolean = {
    val good = expectWithoutFailure(data, expected, msg)
    if (!good) fail
    good
  }

  def expect(data: Data, expected: BigDecimal): Boolean = expect(data, expected, "")
  def expect(data: Data, expected: BigDecimal, msg: String): Boolean = {
    assert(expected <= Double.MaxValue, s"expecting from ${data} a value $expected that is bigger than Double.MaxValue")
    val good = expectWithoutFailure(data, expected.toDouble, msg)
    if (!good) fail
    good
  }

  def expect(data: DspComplex[_], expected: Complex): Boolean = expect(data, expected, msg = "")
  def expect(data: DspComplex[_], expected: Complex, msg: String): Boolean = {
    val dataReal = data.real.asInstanceOf[Data]
    val dataImag = data.imag.asInstanceOf[Data]
    val expectedNewR = roundData(dataReal, expected.real)
    val expectedNewI = roundData(dataImag, expected.imag)
    val path = getName(data)
    val (good, dblValR, dblValI, toleranceR) = updatableDspVerbose.withValue(dispSub) {
      val (dblValR, bitValR) = dspPeek(dataReal)
      val (dblValI, bitValI) = dspPeek(dataImag)
      val (goodR, toleranceR) = checkDecimal(dataReal, expectedNewR, dblValR, bitValR)
      val (goodI, _) = checkDecimal(dataImag, expectedNewI, dblValI, bitValI)
      (goodR & goodI, dblValR, dblValI, toleranceR)
    }
    if (dispDsp || !good) logger info ( { if (!good) Console.RED else "" } +
      s"$msg  EXPECT $path -> $dblValR + $dblValI i == E " +
      s"$expectedNewR + $expectedNewI i ${if (good) "PASS" else "FAIL"}, tolerance = $toleranceR, " +
      s"${bitInfo(data)}" +
      Console.RESET)
    if (!good) fail
    good
  }
}
