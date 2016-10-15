// See LICENSE for license details.

package dsptools

import breeze.math.Complex
import chisel3.internal.firrtl.{KnownBinaryPoint, KnownWidth}
import chisel3._
import chisel3.iotesters.PeekPokeTester
import dsptools.numbers.{DspComplex, DspReal}
import dsptools.Utilities._

class DspTester[T <: Module](c: T,
                             verbose: Boolean = true,
                             base: Int = 16,
                             logFile: Option[java.io.File] = None
                            ) extends PeekPokeTester(c, verbose=verbose, base=base, logFile=logFile) {
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

  def poke(signal: FixedPoint, value: Double): Unit = {
    signal.binaryPoint match {
      case KnownBinaryPoint(binaryPoint) =>
        val bigInt = toBigInt(value, binaryPoint)
        poke(signal, bigInt)
      case _ =>
        throw DspException(s"Error: poke: Can't create FixedPoint for $value, from signal template $signal")
    }
  }

  def dspPoke(bundle: Data, value: Double): Unit = {
    bundle match {
      case s: SInt => {
        val a: BigInt = BigInt(value.round.toInt)
        poke(s, a)
        return
      }
      case f: FixedPoint =>
        f.binaryPoint match {
          case KnownBinaryPoint(binaryPoint) =>
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
        case "SInt" => poke(c.real.asInstanceOf[SInt], value.toInt)
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

      case "SInt" =>
        poke(c.real.asInstanceOf[SInt], value.real.toInt)
        poke(c.imaginary.asInstanceOf[SInt], value.imag.toInt)
      case _ =>
        throw DspException(
          s"poke($c, $value): c DspComplex has unknown underlying type ${c.getClass.getName}")
    }
    println(s"DspPoke($c, $value)")
  }

  def dspPeek(data: Data): Either[Double, Complex] = {
    data match {
      case c: DspComplex[_] =>
        c.underlyingType() match {
          case "fixed" =>
            val real      = dspPeek(c.real.asInstanceOf[FixedPoint]).left.get
            val imaginary = dspPeek(c.imaginary.asInstanceOf[FixedPoint]).left.get
            Right(Complex(real, imaginary))
          case "real"  =>
            val bigIntReal      = dspPeek(c.real.asInstanceOf[DspReal]).left.get
            val bigIntImaginary = dspPeek(c.imaginary.asInstanceOf[DspReal]).left.get
            Right(Complex(bigIntReal, bigIntImaginary))
          case "SInt" =>
            val real = peek(c.real.asInstanceOf[SInt]).toDouble
            val imag = peek(c.imaginary.asInstanceOf[SInt]).toDouble
            Right(Complex(real, imag))
          case _ =>
            throw DspException(
              s"peek($c): c DspComplex has unknown underlying type ${c.getClass.getName}")
        }
      case r: DspReal =>
        val bigInt = super.peek(r.node)
        Left(bigIntBitsToDouble(bigInt))
      case r: FixedPoint =>
        val bigInt = super.peek(r)
        Left(toDouble(bigInt, r.binaryPoint.get))
      case s: SInt =>
        Left(peek(s).toDouble)
      case _ =>
        throw DspException(s"peek($data): data has unknown type ${data.getClass.getName}")
    }
  }

  def dspPeekDouble(data: Data): Double = {
    dspPeek(data) match {
      case Left(double) =>
        double
      case Right(complex) =>
        throw DspException(s"dspPeekDouble($data) returned $complex when expecting double")
    }
  }

  def dspPeekComplex(data: Data): Complex = {
    dspPeek(data) match {
      case Left(double) =>
        throw DspException(s"dspExpectComplex($data) returned $double when expecting complex")
      case Right(complex) =>
        complex
    }
  }

  def peek(signal: FixedPoint): Double = {
    val bigInt = super.peek(signal.asInstanceOf[Bits])
    signal.binaryPoint match {
      case KnownBinaryPoint(binaryPoint) =>
        val double = toDouble(bigInt, binaryPoint)
        double
      case _ =>
        throw DspException(s"Error: peek: Can't peek a FixedPoint, from signal template $signal")
    }
  }

  def dspExpect(data: Data, expected: Double, msg: String): Unit = {
    dspPeek(data) match {
      case Left(double) =>
        println(f"expect got $double%15.8f expect $expected%15.8f")
        expect(double - expected < 0.0001, msg)
      case Right(complex) =>
        throw DspException(s"dspExpect($data, $expected) returned $complex when expecting double")
    }
  }

  def dspExpect(data: DspComplex[_], expected: Complex, msg: String): Unit = {
    dspPeek(data) match {
      case Right(complex) =>
        println(f"expect got $complex expect $expected")
        expect(complex.real - expected.real < 0.0001, msg)
        expect(complex.imag - expected.imag < 0.0001, msg)
      case Left(double) =>
        throw DspException(s"dspExpect($data, $expected) returned $double when expecting complex")
    }
  }
}
