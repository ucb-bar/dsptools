// SPDX-License-Identifier: Apache-2.0

package dsptools.misc

import breeze.math.Complex
import chisel3.{fromDoubleToLiteral => _, fromIntToBinaryPoint => _, _}
import fixedpoint._
import chiseltest.iotesters.PeekPokeTester
import dsptools.DspException
import dsptools.misc.DspTesterUtilities.{getName, roundData, validRangeTest}
import dsptools.numbers._

trait PeekPokeDspExtensions {
  this: PeekPokeTester[_] =>

  private def dspPeek(node: Data): (Double, BigInt) = {
    val bi: BigInt = node match {
      // Unsigned bigint
      case r: DspReal    => peek(r.node.asInstanceOf[Bits])
      case b: Bits       => peek(b.asInstanceOf[Bits])
      case f: FixedPoint => peek(f.asSInt.asInstanceOf[Bits])
    }
    val (dblOut, bigIntOut) = node match {
      case _: DspReal => (DspTesterUtilities.bigIntBitsToDouble(bi), bi)
      case f: FixedPoint =>
        f.binaryPoint match {
          case KnownBinaryPoint(bp) => (FixedPoint.toDouble(bi, bp), bi)
          case _                    => throw DspException("Cannot peek FixedPoint with unknown binary point location")
        }
      // UInt + SInt = Bits
      case _: Bits => (bi.doubleValue, bi)
      case _ => throw DspException(s"Peeked node ${getName(node)} has incorrect type ${node.getClass.getName}")
    }
    (dblOut, bigIntOut)
  }

  def peek(node: FixedPoint): Double = dspPeek(node)._1

  // Takes precedence over Aggregate
  def peek(node: DspReal): Double = dspPeek(node)._1

  // General type returns Double
  def peek(node: Data): Double = dspPeek(node)._1

  def peek(c: DspComplex[_]): Complex = {
    Complex(dspPeek(c.real.asInstanceOf[Data])._1, dspPeek(c.imag.asInstanceOf[Data])._1)
  }

  def poke(signal: FixedPoint, value: Int): Unit = poke(signal, value.toDouble)

  def poke(signal: FixedPoint, value: Double): Unit = poke(signal.asInstanceOf[Data], value)

  // DspReal extends Bundle extends Aggregate extends Data
  // If poking DspReal with Double, can only go here
  // Type classes are all Data:RealBits
  //scalastyle:off cyclomatic.complexity
  def poke(signal: Data, value: Double): Unit = {
    signal match {
      case f: FixedPoint =>
        f.binaryPoint match {
          case KnownBinaryPoint(bp) =>
            poke(f.asSInt.asInstanceOf[Bits], FixedPoint.toBigInt(value, bp))
          case _ => throw DspException("Must poke FixedPoint with known binary point")
        }
      case r: DspReal => poke(r.node.asInstanceOf[Bits], DspTesterUtilities.doubleToBigIntBits(value))
      // UInt + SInt
      case b: Bits => poke(b.asInstanceOf[Bits], BigInt(value.round.toInt))
      case _ => throw DspException("Illegal poke value for node of type Data and value of type Double")
    }
  }

  def poke(signal: Data, value: BigDecimal): Unit = {
    assert(value <= Double.MaxValue, s"poking ${signal} with a value $value bigger than Double.MaxValue")
    poke(signal, value.toDouble)
  }

  def poke(c: DspComplex[_], value: Complex): Unit = {
    poke(c.real.asInstanceOf[Data], value.real)
    poke(c.imag.asInstanceOf[Data], value.imag)
  }

  def pokeFixedPoint(signal: FixedPoint, value: Double): Unit = {
    poke(signal, value)
  }

  def pokeFixedPointBig(signal: FixedPoint, value: BigDecimal): Unit = {
    poke(signal, value)
  }

  def checkDecimal(data: Data, expected: Double, dblVal: Double, bitVal: BigInt): (Boolean, Double) = {
    def toMax(w: Int): BigInt = (BigInt(1) << w) - 1

    // <=
    val fixTol = 0
    val realTol = 8
    val fixTolInt = toMax(fixTol)
    val floTolDec = math.pow(10, -realTol)
    // Error checking does a bad job of handling really small numbers,
    // so let's just force the really small numbers to 0
    val expected0 = if (math.abs(expected) < floTolDec / 100) 0.0 else expected
    val dblVal0 = if (math.abs(dblVal) < floTolDec / 100) 0.0 else dblVal
    val expectedBits = data match {
      case _: DspReal => DspTesterUtilities.doubleToBigIntBits(expected0) // unsigned BigInt
      case f: FixedPoint =>
        f.binaryPoint match {
          case KnownBinaryPoint(bp) => FixedPoint.toBigInt(expected0, bp)
          case _                    => throw DspException("Unknown binary point in FixedPoint on expect")
        }
      case _: Bits => BigInt(expected0.round.toInt)
    }

    validRangeTest(data, expectedBits)

    // Allow for some tolerance in error checking
    val (tolerance, tolDec) = data match {
      case f: FixedPoint =>
        f.binaryPoint match {
          case KnownBinaryPoint(bp) => (fixTolInt, FixedPoint.toDouble(fixTolInt, bp))
          case _                    => throw DspException("Unknown binary point!")
        }
      case _: SInt | _: UInt => (fixTolInt, fixTolInt.toDouble)
      case _: DspReal => (DspTesterUtilities.doubleToBigIntBits(floTolDec), floTolDec)
    }
    val good = {
      if (dblVal0 != expected0) {
        val gotDiffDbl = math.abs(dblVal0 - expected0)
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
    val (dblVal, bitVal) = dspPeek(data)
    val (good, tolerance) = checkDecimal(data, expectedNew, dblVal, bitVal)
    good
  }

  def expect(data: Data, expected: Double, msg: String): Boolean = {
    val good = expectWithoutFailure(data, expected, msg)
    expect(good, msg)
  }

  def expect(signal: FixedPoint, expected: Int): Boolean = expect(signal, expected, "")

  def expect(signal: FixedPoint, expected: Int, msg: String): Boolean = expect(signal, expected.toDouble, msg)

  def expect(signal: FixedPoint, expected: Double): Boolean = expect(signal, expected, "")

  def expect(signal: FixedPoint, expected: Double, msg: String): Boolean = {
    expect(signal.asInstanceOf[Data], expected, msg)
  }

  def expect(data: Data, expected: BigDecimal): Boolean = expect(data, expected, "")

  def expect(data: Data, expected: BigDecimal, msg: String): Boolean = {
    assert(expected <= Double.MaxValue, s"expecting from ${data} a value $expected that is bigger than Double.MaxValue")
    val good = expectWithoutFailure(data, expected.toDouble, msg)
    expect(good, msg)
  }

  def expect(data: DspComplex[_], expected: Complex): Boolean = expect(data, expected, msg = "")

  def expect(data: DspComplex[_], expected: Complex, msg: String): Boolean = {
    val dataReal = data.real.asInstanceOf[Data]
    val dataImag = data.imag.asInstanceOf[Data]
    val expectedNewR = roundData(dataReal, expected.real)
    val expectedNewI = roundData(dataImag, expected.imag)
    val path = getName(data)
    val (good, dblValR, dblValI, toleranceR) = {
      val (dblValR, bitValR) = dspPeek(dataReal)
      val (dblValI, bitValI) = dspPeek(dataImag)
      val (goodR, toleranceR) = checkDecimal(dataReal, expectedNewR, dblValR, bitValR)
      val (goodI, _) = checkDecimal(dataImag, expectedNewI, dblValI, bitValI)
      (goodR & goodI, dblValR, dblValI, toleranceR)
    }
    expect(good, msg)
  }
}
