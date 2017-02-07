// See LICENSE for license details.

package dsptools

import breeze.math.Complex
import dsptools.numbers.{DspComplex, DspReal, Real}
import chisel3._
import chisel3.experimental._
import chisel3.iotesters.TestersCompatibility
import chisel3.internal.InstanceId


// Old stuff
import chisel3.internal.firrtl.KnownBinaryPoint
import chisel3.experimental.FixedPoint
import chisel3.iotesters.PeekPokeTester
import dsptools.DspTesterUtilities._

import scala.util.DynamicVariable

class DspTester[T <: Module](dut: T,
    base: Int = 16,
    logFile: Option[java.io.File] = None) extends PeekPokeTester(dut, base, logFile) with VerilogTbDump {

  val updatableBase = new DynamicVariable[Int](_base)
  private def dispBase = updatableBase.value

  // Verbosity @ DSP level (different from chisel-testers verbosity)
  val updatableDSPVerbose = new DynamicVariable[Boolean](dsptestersOpt.isVerbose)
  private def dispDSP = updatableDSPVerbose.value

  // Verbosity @ normal peek/poke level (chisel-testers verbosity) -- for additionally displaying as bits
  val updatableBitVerbose = new DynamicVariable[Boolean](iotestersOM.testerOptions.isVerbose)
  private def dispBits = updatableBitVerbose.value

  val fixTolLSBs = new DynamicVariable[Int](dsptestersOpt.fixTolLSBs)
  private def fixTol = fixTolLSBs.value

  val realTolDecPts = new DynamicVariable[Int](dsptestersOpt.realTolDecPts)
  private def realTol = realTolDecPts.value

  // Tester starts with reset (but don't count cycles during reset)
  super.reset(5)

  private def getName(signal: InstanceId): String = {
    s"${signal.parentPathName}.${TestersCompatibility.validName(signal.instanceName)}"
  }

  private def validRangeTest(signal: Bits, value: BigInt) {
    val len = value.bitLength
    val neededLen = if (isSigned(signal)) len + 1 else len
    if (neededLen > signal.getWidth) 
      throw DspException(s"Poke/Expect value of ${getName(signal)} is not in node range")
  }

  // TODO: Get rid of duplication in chisel-testers
  private def signConvert(signal: Bits, bigInt: BigInt): BigInt = {
    val width = signal.getWidth
    val signFix = {
      if(bigInt.bitLength >= width) - ((BigInt(1) << width) - bigInt)
      else bigInt
    }
    if (isSigned(signal)) signFix else bigInt
  }

  ///////////////// OVERRIDE UNDERLYING FUNCTIONS FROM PEEK POKE TESTER /////////////////

  override def step(n: Int) {
    if (dispDSP) logger println s"STEP ${n}x -> ${t+n}"
    stepPrint(n)
    backend.step(n)
    incTime(n)
  }

  override def reset(n: Int = 1) {
    if (dispDSP) logger println s"RESET ${n}x -> ${t+n}"
    resetPrint(n)
    backend.reset(n)
    incTime(n)
  }

  override def poke(path: String, value: BigInt): Unit = {
    if (verilogTb) throw DspException("Don't use poke path (String) when printing verilog tb")
    backend.poke(path, value)(logger, dispDSP, dispBase)
  }

  override def poke(signal: Bits, value: BigInt): Unit = {
    // bit-level poke is displayed as unsigned
    validRangeTest(signal, value)
    if (!signal.isLit) backend.poke(signal, value, None)(logger, dispDSP, dispBase)
    pokePrint(signal, value)
  }

  // Poke at does not involve external signals -- no VerilogTB print
  override def pokeAt[TT <: Bits](data: Mem[TT], value: BigInt, off: Int): Unit = {
    backend.poke(data, value, Some(off))(logger, dispDSP, dispBase)
  }

  override def peek(path: String) = {
    if (verilogTb) throw DspException("Don't use peek path (String) when printing verilog tb")
    backend.peek(path)(logger, dispDSP, dispBase)
  }

  override def peek(signal: Bits): BigInt = {
    val o = {
      // bit-level peek is displayed as unsigned
      if (!signal.isLit) backend.peek(signal, None)(logger, dispDSP, dispBase) 
      else {
        val litVal = signal.litValue()
        if (dispDSP) 
          logger println s"  PEEK ${getName(signal)} -> ${TestersCompatibility.bigIntToStr(litVal, dispBase)}"
        litVal
      }
    }
    peekPrint(signal, o)
    o
  }

  override def peek(signal: Aggregate): IndexedSeq[BigInt] =  {
    TestersCompatibility.flatten(signal) map { x => peek(x) }
  }

  // Peek at does not involve external signals -- no VerilogTB print
  override def peekAt[TT <: Bits](data: Mem[TT], off: Int): BigInt = {
    backend.peek(data, Some(off))(logger, dispDSP, dispBase)
  }

  override def expect(good: Boolean, msg: => String): Boolean = {
    if (dispDSP || !good) logger println ( { if (!good) Console.RED else "" } +
      s"""EXPECT AT $t $msg ${if (good) "PASS" else "FAIL"}""" + Console.RESET)
    if (!good) fail
    good
  }

  override def expect(signal: Bits, expected: BigInt, msg: => String = ""): Boolean = {
    validRangeTest(signal, expected)
    val path = getName(signal)
    val got = updatableDSPVerbose.withValue(false) { peek(signal) }
    val good = got == expected  
    if (dispDSP || !good) logger println ( { if (!good) Console.RED else "" } +
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

  // TODO: Clean up naming, consolidate methods

  def poke(signal: FixedPoint, value: Double): Unit = {
    dspPoke(signal, value)
  }

  def dspPoke(bundle: Data, value: Double): Unit = {
    updatableDSPVerbose.withValue(dispBits) {
      dspPokeOld(bundle, value)
    }
    if (dispDSP) logger println s"  POKE ${getName(bundle)} <- ${value}" 
  }

  def dspPoke(c: DspComplex[_], value: Complex): Unit = {
    updatableDSPVerbose.withValue(dispBits) {
      dspPokeOld(c, value)
    }
    if (dispDSP) logger println s"  POKE ${getName(c)} <- ${value.toString}" 
  }

  def dspPeek(data: Data): Either[Double, Complex] = {
    val peekedVal = updatableDSPVerbose.withValue(dispBits) {
      dspPeekOld(data)
    }
    if (dispDSP) {
      val value = peekedVal match {
        case Left(dbl) => dbl.toString
        case Right(cplx) => cplx.toString
      }
      logger println s"  PEEK ${getName(data)} -> ${value}" 
    }
    peekedVal
  }

  def dspPeekDouble(data: Data): Double = {
    dspPeek(data) match {
      case Left(dbl) => dbl
      case _ => throw DspException(s"dspPeekDouble data type can't be DSPComplex")
    }  
  }

  def dspPeekComplex(data: Data): Complex = {
    dspPeek(data) match {
      case Right(cplx) => cplx
      case _ => throw DspException(s"dspPeekComplex data type can't be T <: Data:Real")
    } 
  }

  def peek(signal: FixedPoint): Double = {
    dspPeekDouble(signal)
  }

  ///////////////// SPECIALIZED DSP EXPECT /////////////////

  // TODO; eliminate silly peek/pokes above

  // For custom DSP expect to work, need BigInt value too (not provided by original method)
  def expectDspPeek(node: Data): (Double, BigInt) = {
    updatableDSPVerbose.withValue(dispBits) {
      node match {
        case r: DspReal => {
          val bi = peek(r.node)                             // unsigned bigint
          (DspTesterUtilities.bigIntBitsToDouble(bi), bi)  
        }
        case f: FixedPoint => {
          val bi = peek(f.asInstanceOf[Bits])
          (FixedPoint.toDouble(bi, f.binaryPoint.get), bi)
        }
        case s: SInt => {
          val bi = peek(s)
          (bi.toDouble, bi)
        }
        case _ => throw DspException("Peeked node ${getName(node)} has incorrect type ${node.getClass.getName}")
      }
    }
  }

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
      case s: SInt => BigInt(expected0.round.toInt)
    }

    data match {
      case _: FixedPoint | _: SInt => 
        if (expectedBits.bitLength > data.getWidth-1) throw DspException("Expected value is out of output node range")
      case _ =>
    }

    // Allow for some tolerance in error checking
    val (tolerance, tolDec) = data match {
      case f: FixedPoint => (fixTolInt, FixedPoint.toDouble(fixTolInt, f.binaryPoint.get))
      case s: SInt => (fixTolInt, fixTolInt.toDouble)
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
      case s: SInt => expected.round
      case _ => expected
    }
  }

  def dspExpect(data: Data, expected: Double): Boolean = dspExpect(data, expected, msg = "")
  def dspExpect(data: Data, expected: Double, msg: String): Boolean = {
    val expectedNew = roundExpected(data, expected)
    val path = getName(data)
    val (dblVal, bitVal) = expectDspPeek(data)
    val (good, tolerance) = checkDecimal(data, expectedNew, dblVal, bitVal)
    if (dispDSP || !good) logger println ( { if (!good) Console.RED else "" } + 
      s"""${msg}  EXPECT ${path} -> $dblVal == """ +
        s"""$expectedNew ${if (good) "PASS" else "FAIL"}, tolerance = $tolerance""" + Console.RESET)
    if (!good) fail
    good
  }

  def dspExpect(data: DspComplex[_], expected: Complex): Boolean = dspExpect(data, expected, msg = "")
  def dspExpect(data: DspComplex[_], expected: Complex, msg: String): Boolean = {
    val dataReal = data.real.asInstanceOf[Data]
    val dataImag = data.imag.asInstanceOf[Data]
    val expectedNewR = roundExpected(dataReal, expected.real)
    val expectedNewI = roundExpected(dataImag, expected.imag)
    val path = getName(data)
    val (dblValR, bitValR) = expectDspPeek(dataReal) 
    val (dblValI, bitValI) = expectDspPeek(dataImag)
    val (goodR, toleranceR) = checkDecimal(dataReal, expectedNewR, dblValR, bitValR)
    val (goodI, toleranceI) = checkDecimal(dataImag, expectedNewI, dblValI, bitValI)
    val good = goodR & goodI
    if (dispDSP || !good) logger println ( { if (!good) Console.RED else "" } + 
      s"""${msg}  EXPECT ${path} -> $dblValR + $dblValI i == """ +
        s"""$expectedNewR + $expectedNewI i ${if (good) "PASS" else "FAIL"}, tolerance = $toleranceR""" + 
          Console.RESET)   
    if (!good) fail
    good 
  }

//////////////////////////////////////

def dspPokeOld(bundle: Data, value: Double): Unit = {
    bundle match {
      case u: UInt => 
        poke(u, BigInt(math.abs(value.round).toInt))
      case s: SInt =>
        val a: BigInt = BigInt(value.round.toInt)
        poke(s, a)
      case f: FixedPoint =>
        f.binaryPoint match {
          case KnownBinaryPoint(binaryPoint) =>
            val bigInt = FixedPoint.toBigInt(value, binaryPoint)
            poke(f, bigInt)
          case _ =>
            throw DspException(s"Error: poke: Can't create FixedPoint for $value, from signal template $bundle")
        }
      case r: DspReal =>
        poke(r.node, doubleToBigIntBits(value))
      case c: DspComplex[_]  => c.underlyingType() match {
        case "fixed" => poke(c.real.asInstanceOf[FixedPoint], value)
        case "real"  => dspPoke(c.real.asInstanceOf[DspReal], value)
        case "SInt" => poke(c.real.asInstanceOf[SInt], value.round.toInt)
        case _ =>
          throw DspException(
            s"poke($bundle, $value): bundle DspComplex has unknown underlying type ${bundle.getClass.getName}")
      }
      case _ =>
        throw DspException(s"poke($bundle, $value): bundle has unknown type ${bundle.getClass.getName}")
    }
  }

  def dspPokeOld(c: DspComplex[_], value: Complex): Unit = {
    c.underlyingType() match {
      case "fixed" =>
        dspPoke(c.real.asInstanceOf[FixedPoint], value.real)
        dspPoke(c.imag.asInstanceOf[FixedPoint], value.imag)
      case "real"  =>
        dspPoke(c.real.asInstanceOf[DspReal], value.real)
        dspPoke(c.imag.asInstanceOf[DspReal], value.imag)
      case "SInt" =>
        poke(c.real.asInstanceOf[SInt], value.real.round.toInt)
        poke(c.imag.asInstanceOf[SInt], value.imag.round.toInt)
      case _ =>
        throw DspException(
          s"poke($c, $value): c DspComplex has unknown underlying type ${c.getClass.getName}")
    }
    //scalastyle:off regex
    if (_verbose) {
      println(s"DspPoke($c, $value)")
    }
    //scalastyle:on regex
  }

def dspPeekOld(data: Data): Either[Double, Complex] = {
    data match {
      case c: DspComplex[_] =>
        c.underlyingType() match {
          case "fixed" =>
            val real      = dspPeek(c.real.asInstanceOf[FixedPoint]).left.get
            val imag = dspPeek(c.imag.asInstanceOf[FixedPoint]).left.get
            Right(Complex(real, imag))
          case "real"  =>
            val bigIntReal      = dspPeek(c.real.asInstanceOf[DspReal]).left.get
            val bigIntimag = dspPeek(c.imag.asInstanceOf[DspReal]).left.get
            Right(Complex(bigIntReal, bigIntimag))
          case "SInt" =>
            val real = peek(c.real.asInstanceOf[SInt]).toDouble
            val imag = peek(c.imag.asInstanceOf[SInt]).toDouble
            Right(Complex(real, imag))
          case _ =>
            throw DspException(
              s"peek($c): c DspComplex has unknown underlying type ${c.getClass.getName}")
        }
      case r: DspReal =>
        val bigInt = peek(r.node)
        Left(bigIntBitsToDouble(bigInt))
      case r: FixedPoint =>
        val bigInt = peek(r.asInstanceOf[Bits])
        Left(FixedPoint.toDouble(bigInt, r.binaryPoint.get))
      case s: SInt =>
        Left(peek(s).toDouble)
      case _ =>
        throw DspException(s"peek($data): data has unknown type ${data.getClass.getName}")
    }
  }

  
  
}
