// See LICENSE for license details.

package dsptools

import chisel3.internal.firrtl.{KnownBinaryPoint, BinaryPoint, KnownWidth}
import chisel3.{Bits, Module}
import chisel3.core.{FixedPoint, SInt}
import chisel3.iotesters.{Backend, PeekPokeTester}

class DspTester[T <: Module](c: T) extends PeekPokeTester(c) {
  def toBigInt(x: Double, fractionalWidth: Int): BigInt = {
    val multiplier = math.pow(2,fractionalWidth)
    val result = BigInt(math.round(x * multiplier))
    //    println(s"toBigInt:x = $x, width = $fractionalWidth multiplier $multiplier result $result")
    result
  }

  def toDouble(i: BigInt, fractionalWidth: Int): Double = {
    val multiplier = math.pow(2,fractionalWidth)
    val result = i.toDouble / multiplier
    //    println(s"toDouble:i = $i, fw = $fractionalWidth, mult = $multiplier, result $result")
    result
  }

  def poke(signal: FixedPoint, value: Double): Unit = {
    println(s"signal is $signal")
    (signal.width, signal.binaryPoint) match {
      case (KnownWidth(width), KnownBinaryPoint(binaryPoint)) =>
        val bigInt = toBigInt(value, binaryPoint)
        poke(signal, bigInt)
      case _ =>
        throw DspException(s"Error: poke: Can't create FixedPoint for $value, from signal template $signal")
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

}
