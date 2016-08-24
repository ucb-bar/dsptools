// See LICENSE for license details.

package dsptools

import chisel3.internal.firrtl.{KnownBinaryPoint, KnownWidth}
import chisel3.{Data, Bits, Module}
import chisel3.core.FixedPoint
import chisel3.iotesters.PeekPokeTester
import dsptools.numbers.{DspComplex, DspReal}
import spire.algebra.Ring

class DspTester[T <: Module](c: T) extends PeekPokeTester(c) {
  def toBigInt(x: Double, fractionalWidth: Int): BigInt = {
    val multiplier = math.pow(2,fractionalWidth)
    val result = BigInt(math.round(x * multiplier))
    // println(s"toBigInt:x = $x, width = $fractionalWidth multiplier $multiplier result $result")
    result
  }

  def toDouble(i: BigInt, fractionalWidth: Int): Double = {
    val multiplier = math.pow(2,fractionalWidth)
    val result = i.toDouble / multiplier
    // println(s"toDouble:i = $i, fw = $fractionalWidth, multiplier = $multiplier, result $result")
    result
  }

  def doubleToBigIntBits(double: Double): BigInt = {
    BigInt(java.lang.Double.doubleToLongBits(double))
  }

  def bigIntBitsToDouble(bigInt: BigInt): Double = {
    java.lang.Double.longBitsToDouble(bigInt.toLong)
  }

  def poke(signal: FixedPoint, value: Double): Unit = {
    (signal.width, signal.binaryPoint) match {
      case (KnownWidth(width), KnownBinaryPoint(binaryPoint)) =>
        val bigInt = toBigInt(value, binaryPoint)
        poke(signal, bigInt)
      case _ =>
        throw DspException(s"Error: poke: Can't create FixedPoint for $value, from signal template $signal")
    }
  }

  def poke(signal: DspReal, value: Double): Unit = {
    val bigInt = doubleToBigIntBits(value)
    poke(signal.node, bigInt)
  }

  def poke(signal: DspComplex[FixedPoint], value: Double): Unit = {
    val bigInt = doubleToBigIntBits(value)
    poke(signal.real, bigInt)
    poke(signal.imaginary, bigInt)
  }

  def poke[T <: Data:Ring](signal: T, value: Double): Unit = {
    signal match {
      case d: DspReal => poke(d, value)
      case f: FixedPoint => poke(f, value)
      case c: DspComplex[FixedPoint] => poke(c, value)

    }
  }

  def peek(signal: FixedPoint): Double = {
    val bigInt = super.peek(signal.asInstanceOf[Bits])
    (signal.width, signal.binaryPoint) match {
      case (KnownWidth(width), KnownBinaryPoint(binaryPoint)) =>
        val double = toDouble(bigInt, binaryPoint)
        double
      case _ =>
        throw DspException(s"Error: peek: Can't peek a FixedPoint, from signal template $signal")
    }
  }
  def peek(signal: DspReal): Double = {
    val bigInt = super.peek(signal.node)
    bigIntBitsToDouble(bigInt)
  }

  def peek(signal: DspComplex[FixedPoint]): Double = {
    val bigInt = super.peek(signal.real)
    bigIntBitsToDouble(bigInt)
  }

  def peek[T <: Data:Ring](signal: T): Double = {
    signal match {
      case d: DspReal => peek(d)
      case f: FixedPoint => peek(f)
      case c: DspComplex[FixedPoint] => peek(c)
    }
  }

  def expect(signal: DspReal, expected: Double, msg: String): Unit = {
    val out = peek(signal)
    println(f"expect got $out%15.8f expect $expected%15.8f")
    expect(out - expected < 0.0001, msg)
  }

}
