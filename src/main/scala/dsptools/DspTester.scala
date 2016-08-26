// See LICENSE for license details.

package dsptools

import breeze.math.Complex
import chisel3.internal.firrtl.{KnownBinaryPoint, KnownWidth}
import chisel3._
import chisel3.core.FixedPoint
import chisel3.iotesters.PeekPokeTester
import dsptools.numbers.{DspComplex, DspReal}

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

  def dspPoke(bundle: Data, value: Double): Unit = {
    bundle match {
      case f: FixedPoint =>
        (f.width, f.binaryPoint) match {
          case (KnownWidth(width), KnownBinaryPoint(binaryPoint)) =>
            val bigInt = toBigInt(value, binaryPoint)
            poke(f, bigInt)
          case _ =>
            throw DspException(s"Error: poke: Can't create FixedPoint for $value, from signal template $bundle")
        }

      case r: DspReal =>
        val bigInt = doubleToBigIntBits(value)
        poke(r.node, bigInt)
      case c: DspComplex[_]  => c.underlyingType() match {
        case "fixed" => poke(c.real.asInstanceOf[FixedPoint], value)
        case "real"  => dspPoke(c.real.asInstanceOf[DspReal], value)
        //        case "SInt" => poke(c.real.asInstanceOf[SInt], value)
        case _ =>
          throw DspException(
            s"poke($bundle, $value): bundle DspComplex has unknown underlying type ${bundle.getClass.getName}")
      }
      case _ =>
        throw DspException(s"poke($bundle, $value): bundle has unknown type ${bundle.getClass.getName}")
    }
  }

  def dspPoke(c: DspComplex[_], value: Complex): Unit = {
    c.underlyingType() match {
      case "fixed" =>
        dspPoke(c.real.asInstanceOf[FixedPoint], value.real)
        dspPoke(c.imaginary.asInstanceOf[FixedPoint], value.imag)
      case "real"  =>
        dspPoke(c.real.asInstanceOf[DspReal], value.real)
        dspPoke(c.imaginary.asInstanceOf[DspReal], value.imag)

      //        case "SInt" => poke(c.real.asInstanceOf[SInt], value)
      case _ =>
        throw DspException(
          s"poke($c, $value): c DspComplex has unknown underlying type ${c.getClass.getName}")
    }
  }

  def dspPeek(bundle: Data): Double = {
    bundle match {
      case r: DspReal =>
        val bigInt = super.peek(r.node)
        bigIntBitsToDouble(bigInt)
      case r: FixedPoint =>
        val bigInt = super.peek(r)
        bigIntBitsToDouble(bigInt)
      case _ =>
        throw DspException(s"peek($bundle): bundle has unknown type ${bundle.getClass.getName}")
    }
  }
  def dspPeekComplex(c: DspComplex[_]): Complex = {
    c.underlyingType() match {
      case "fixed" =>
        val real      = dspPeek(c.real.asInstanceOf[FixedPoint])
        val imaginary = dspPeek(c.imaginary.asInstanceOf[FixedPoint])
        Complex(real, imaginary)
      case "real"  =>
        val bigIntReal      = dspPeek(c.real.asInstanceOf[DspReal])
        val bigIntImaginary = dspPeek(c.imaginary.asInstanceOf[DspReal])
        Complex(bigIntReal, bigIntImaginary)
      //        case "SInt" => poke(c.real.asInstanceOf[SInt], value)
      case _ =>
        throw DspException(
          s"peek($c): c DspComplex has unknown underlying type ${c.getClass.getName}")
    }
  }

  //  def dspPeekNumber[T <: Data](signal: T): T = {
//    signal match {
//      case r: DspReal =>
//        val bigInt = super.peek(r.node)
//        DspReal(bigIntBitsToDouble(bigInt)).asInstanceOf[T]
//      case r: FixedPoint =>
//        super.peek(r).asInstanceOf[T]
//      case c: DspComplex[_]  => c.underlyingType() match {
//        case "fixed" =>
//          val real = dspPeekNumber(c.real.asInstanceOf[FixedPoint])
//          val imaginary = dspPeekNumber(c.imaginary.asInstanceOf[FixedPoint])
//          DspComplex(real, imaginary).asInstanceOf[T]
//        case "real"  => dspPeek(c.real.asInstanceOf[DspReal])
//          val real = dspPeekNumber(c.real.asInstanceOf[DspReal])
//          val imaginary = dspPeekNumber(c.imaginary.asInstanceOf[DspReal])
//          DspComplex(real, imaginary).asInstanceOf[T]
//        case _ =>
//          throw DspException(
//            s"peek($signal): signal DspComplex has unknown underlying type ${signal.getClass.getName}")
//      }
//      case _ =>
//        throw DspException(s"peek($signal): signal has unknown type ${signal.getClass.getName}")
//    }
//  }

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

  def dspExpect(bundle: Data, expected: Double, msg: String): Unit = {
    val result = dspPeek(bundle)

    println(f"expect got $result%15.8f expect $expected%15.8f")
    expect(result - expected < 0.0001, msg)
  }
}
