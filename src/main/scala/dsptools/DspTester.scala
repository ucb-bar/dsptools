// See LICENSE for license details.

package dsptools

import breeze.math.Complex
import dsptools.numbers.{DspComplex, DspReal}
import chisel3._
import chisel3.experimental.FixedPoint
import chisel3.internal.InstanceId
import chisel3.internal.firrtl.KnownBinaryPoint
import chisel3.iotesters.PeekPokeTester
import scala.util.DynamicVariable

// TODO: Get rid of
import chisel3.iotesters.TestersCompatibility

class DspTester[T <: Module](
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
  super.reset(5)

  private def getName(signal: InstanceId): String = {
    s"${signal.parentPathName}.${TestersCompatibility.validName(signal.instanceName)}"
  }

  private def validRangeTest(signal: Data, value: BigInt) {
    val len = value.bitLength
    val neededLen = if (isSigned(signal)) len + 1 else len
    require(signal.widthOption.nonEmpty, "Cannot Poke/Expect node of unknown width!")
    if (neededLen > signal.getWidth) 
      throw DspException(s"Poke/Expect value of ${getName(signal)} is not in node range")
    if (!isSigned(signal) && value < 0)
      throw DspException("Poking/Expecting negative value to an unsigned input")
  }

  def bitInfo(signal: Data): String = signal.widthOption match {
    case Some(width) => {
      signal match {
        case f: FixedPoint => f.binaryPoint match {
          // Q integer . fractional bits
          case KnownBinaryPoint(bp) => s"Q${width - 1 - bp}.$bp"
          case _ => s"${width}-bit F"
        }
        case r: DspReal => "R"
        case u: UInt => s"${width}-bit U"
        case s: SInt => s"${width}-bit S"
        case c: DspComplex[_] => {
          val realInfo = bitInfo(c.real.asInstanceOf[Data])
          val imagInfo = bitInfo(c.imag.asInstanceOf[Data])
          s"[$realInfo, $imagInfo]"
        }
        case _ => throw DspException("Can't get bit info! Invalid type!")
      }
    }
    case None => ""
  }

  ///////////////// OVERRIDE UNDERLYING FUNCTIONS FROM PEEK POKE TESTER /////////////////

  override def step(n: Int) {
    if (dispDsp) logger println s"STEP ${n}x -> ${t+n}"
    stepPrint(n)
    backend.step(n)
    incTime(n)
  }

  override def reset(n: Int = 1) {
    if (dispDsp) logger println s"RESET ${n}x -> ${t+n}"
    resetPrint(n)
    backend.reset(n)
    incTime(n)
  }

  override def poke(path: String, value: BigInt): Unit = {
    if (verilogTb) throw DspException("Don't use poke path (String) when printing verilog tb")
    backend.poke(path, value)(logger, dispDsp, dispBase)
  }

  override def poke(signal: Bits, value: BigInt): Unit = {
    // bit-level poke is displayed as unsigned
    validRangeTest(signal, value)
    if (!signal.isLit) backend.poke(signal, value, None)(logger, dispDsp, dispBase)
    pokePrint(signal, value)
  }

  // Poke at does not involve external signals -- no VerilogTB print
  override def pokeAt[TT <: Bits](data: Mem[TT], value: BigInt, off: Int): Unit = {
    backend.poke(data, value, Some(off))(logger, dispDsp, dispBase)
  }

  override def peek(path: String) = {
    if (verilogTb) throw DspException("Don't use peek path (String) when printing verilog tb")
    backend.peek(path)(logger, dispDsp, dispBase)
  }

  override def peek(signal: Bits): BigInt = {
    val o = {
      // bit-level peek is displayed as unsigned
      if (!signal.isLit) backend.peek(signal, None)(logger, dispDsp, dispBase)
      else {
        val litVal = signal.litValue()
        if (dispDsp)
          logger println s"  PEEK ${getName(signal)} -> ${TestersCompatibility.bigIntToStr(litVal, dispBase)}"
        litVal
      }
    }
    peekPrint(signal, o)
    o
  }

  override def peek(signal: Aggregate): IndexedSeq[BigInt] =  {
    // Flatten returns IndexSeq[Bits], so will use above peek
    TestersCompatibility.flatten(signal) map { x => peek(x) }
  }

  // Peek at does not involve external signals -- no VerilogTB print
  override def peekAt[TT <: Bits](data: Mem[TT], off: Int): BigInt = {
    backend.peek(data, Some(off))(logger, dispDsp, dispBase)
  }

  override def expect(good: Boolean, msg: => String): Boolean = {
    if (dispDsp || !good) logger println ( { if (!good) Console.RED else "" } +
      s"""EXPECT AT $t $msg ${if (good) "PASS" else "FAIL"}""" + Console.RESET)
    if (!good) fail
    good
  }

  override def expect(signal: Bits, expected: BigInt, msg: => String = ""): Boolean = {
    validRangeTest(signal, expected)
    val path = getName(signal)
    val got = updatableDspVerbose.withValue(false) { peek(signal) }
    val good = got == expected  
    if (dispDsp || !good) logger println ( { if (!good) Console.RED else "" } +
      s"""${msg}  EXPECT ${path} -> ${TestersCompatibility.bigIntToStr(got, dispBase)} == """ +
        s"""${TestersCompatibility.bigIntToStr(expected, dispBase)} ${if (good) "PASS" else "FAIL"}""" + Console.RESET)
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
  def poke(signal: UInt, value: Int): Unit = poke(signal, value.toDouble)
  def poke(signal: SInt, value: Int): Unit = poke(signal, value.toDouble)

  // Has priority over Bits (FixedPoint extends Bits)
  def poke(signal: FixedPoint, value: Double): Unit = poke(signal.asInstanceOf[Data], value)
  
  // DspReal extends Bundle extends Aggregate extends Data
  // If poking DspReal with Double, can only go here
  // Type classes are all Data:RealBits
  def poke(signal: Data, value: Double): Unit = {
    updatableDspVerbose.withValue(dispSub) {
      signal match {
        case f: FixedPoint => {
          f.binaryPoint match {
            case KnownBinaryPoint(bp) => poke(f, FixedPoint.toBigInt(value, bp))
            case _ => throw DspException("Must poke FixedPoint with known binary point")
          }
        }
        case r: DspReal => poke(r.node, DspTesterUtilities.doubleToBigIntBits(value))
        // UInt + SInt
        case b: Bits => poke(b, BigInt(value.round.toInt))
        case _ => throw DspException("Illegal poke value for node of type Data and value of type Double")
      }      
    }
    if (dispDsp) logger println s"  POKE ${getName(signal)} <- ${value} \t ${bitInfo(signal)}"
  }

  // Will only print individual real/imag peek information if dispSub is true!
  def poke(c: DspComplex[_], value: Complex): Unit = {
    updatableDspVerbose.withValue(dispSub) {
      poke(c.real.asInstanceOf[Data], value.real)
      poke(c.imag.asInstanceOf[Data], value.imag)
    }
    if (dispDsp) logger println s"  POKE ${getName(c)} <- ${value.toString} \t ${bitInfo(c)}"
  }

  private def dspPeek(node: Data): (Double, BigInt) = {
    val bi: BigInt = updatableDspVerbose.withValue(dispSub) {
      node match {
        // Unsigned bigint
        case r: DspReal => peek(r.node.asInstanceOf[Bits])
        case b: Bits => peek(b.asInstanceOf[Bits])
      }
    }
    val (dblOut, bigIntOut) = node match {
      case r: DspReal => (DspTesterUtilities.bigIntBitsToDouble(bi), bi)  
      case f: FixedPoint => f.binaryPoint match {
        case KnownBinaryPoint(bp) => (FixedPoint.toDouble(bi, bp), bi)
        case _ => throw DspException("Cannot peek FixedPoint with unknown binary point location")
      }
      // UInt + SInt = Bits
      case b: Bits => (bi.doubleValue, bi)
      case _ => throw DspException("Peeked node ${getName(node)} has incorrect type ${node.getClass.getName}")
    }
    if (dispDsp) logger println s"  PEEK ${getName(node)} -> ${dblOut} \t ${bitInfo(node)}" 
    (dblOut, bigIntOut)
  }

  // Takes precedence over Bits
  def peek(node: Bool): Boolean = {
    if (peek(node.asInstanceOf[Bits]) == 1) true else false
  }
  def peek(node: UInt): Int = dspPeek(node)._1.toInt
  def peek(node: SInt): Int = dspPeek(node)._1.toInt
  def peek(node: FixedPoint): Double = dspPeek(node)._1
  // Takes precedence over Aggregate
  def peek(node: DspReal): Double = dspPeek(node)._1

  // General type returns Double
  def peek(node: Data): Double = dspPeek(node)._1

  def peek(c: DspComplex[_]): Complex = {
    val out = updatableDspVerbose.withValue(dispSub) {
      Complex(dspPeek(c.real.asInstanceOf[Data])._1, dspPeek(c.imag.asInstanceOf[Data])._1)
    }
    if (dispDsp) logger println s"  PEEK ${getName(c)} <- ${out.toString} \t ${bitInfo(c)}"
    out
  }

  def expect(signal: Bool, expected: Boolean): Boolean = expect(signal, expected, "")
  def expect(signal: Bool, expected: Boolean, msg: String): Boolean = {
    expect(signal, if (expected) BigInt(1) else BigInt(0))
  }







 


  // Need to specify what happens when you poke with an Int
  // Display as Double (so you can separate out number representation vs. bit representation)
  //def poke(signal: UInt, value: Int): Unit = poke(signal, value.toDouble)
  //def poke(signal: SInt, value: Int): Unit = poke(signal, value.toDouble)

  // Has priority over Bits (FixedPoint extends Bits)
  //def poke(signal: FixedPoint, value: Double): Unit = signal match {
  //  case d: Data => poke(d.asInstanceOf[Data], value)
  //}


// sint expect exact? 
  //addbitinfo
    
    // sint, uint ok; needs a cast fir int to bigint or double??  bigint consrvative
// fixed need separate
// dspreal defers here
  


  




 
  

  
  
  

  ///////////////// SPECIALIZED DSP EXPECT /////////////////

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
      case r: DspReal => DspTesterUtilities.doubleToBigIntBits(expected0)  // unsigned BigInt
      case f: FixedPoint => FixedPoint.toBigInt(expected0, f.binaryPoint.get)
      case b: Bits => BigInt(expected0.round.toInt)
    }

    validRangeTest(data, expectedBits)

    // Allow for some tolerance in error checking
    val (tolerance, tolDec) = data match {
      case f: FixedPoint => (fixTolInt, FixedPoint.toDouble(fixTolInt, f.binaryPoint.get))
      case _: SInt | _: UInt => (fixTolInt, fixTolInt.toDouble)
      case _ => (DspTesterUtilities.doubleToBigIntBits(floTolDec), floTolDec)
    }
    val good = {
      if (dblVal0 != expected0) {
        val gotDiffDbl = math.abs(dblVal0-expected0)
        val gotDiffBits = (bitVal - expectedBits).abs
        val passDbl = gotDiffDbl <= tolDec
        val passBits = gotDiffBits <= tolerance
        passDbl & passBits
      }
      else true
    }
    (good, tolDec)
  }

  // Keep consistent with poke
  private def roundExpected(data: Data, expected: Double): Double = {
    data match {
      case _: SInt | _: UInt => expected.round
      case _ => expected
    }
  }

  def expect(data: Data, expected: Double): Boolean = expect(data, expected, msg = "")
  def expect(data: Data, expected: Double, msg: String): Boolean = {
    val expectedNew = roundExpected(data, expected)
    val path = getName(data)
    val (dblVal, bitVal) = updatableDspVerbose.withValue(dispSub) { dspPeek(data) }
    val (good, tolerance) = checkDecimal(data, expectedNew, dblVal, bitVal)
    if (dispDsp || !good) logger println ( { if (!good) Console.RED else "" } +
      s"""${msg}  EXPECT ${path} -> $dblVal == """ +
        s"""$expectedNew ${if (good) "PASS" else "FAIL"}, tolerance = $tolerance""" + Console.RESET)
    if (!good) fail
    good
  }

  def expect(data: DspComplex[_], expected: Complex): Boolean = expect(data, expected, msg = "")
  def expect(data: DspComplex[_], expected: Complex, msg: String): Boolean = {
    val dataReal = data.real.asInstanceOf[Data]
    val dataImag = data.imag.asInstanceOf[Data]
    val expectedNewR = roundExpected(dataReal, expected.real)
    val expectedNewI = roundExpected(dataImag, expected.imag)
    val path = getName(data)
    val (dblValR, bitValR) = updatableDspVerbose.withValue(dispSub) { dspPeek(dataReal) }
    val (dblValI, bitValI) = updatableDspVerbose.withValue(dispSub) { dspPeek(dataImag) }
    val (goodR, toleranceR) = checkDecimal(dataReal, expectedNewR, dblValR, bitValR)
    val (goodI, toleranceI) = checkDecimal(dataImag, expectedNewI, dblValI, bitValI)
    val good = goodR & goodI
    if (dispDsp || !good) logger println ( { if (!good) Console.RED else "" } +
      s"""${msg}  EXPECT ${path} -> $dblValR + $dblValI i == """ +
        s"""$expectedNewR + $expectedNewI i ${if (good) "PASS" else "FAIL"}, tolerance = $toleranceR""" + 
          Console.RESET)   
    if (!good) fail
    good 
  }  
}
