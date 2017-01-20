// See LICENSE for license details.

package dsptools

import breeze.math.Complex
import chisel3.internal.firrtl.KnownBinaryPoint
import chisel3._
import chisel3.iotesters.PeekPokeTester
import dsptools.numbers.{DspComplex, DspReal}
import dsptools.Utilities._

class DspTester[T <: Module](c: T,
                             base: Int = 16,
                             logFile: Option[java.io.File] = None
                            ) extends PeekPokeTester(c, base=base, logFile=logFile) {
  def poke(signal: FixedPoint, value: Double): Unit = {
    signal.binaryPoint match {
      case KnownBinaryPoint(binaryPoint) =>
        val bigInt = FixedPoint.toBigInt(value, binaryPoint)
        poke(signal, bigInt)
      case _ =>
        throw DspException(s"Error: poke: Can't create FixedPoint for $value, from signal template $signal")
    }
  }

  //scalastyle:off cyclomatic.complexity
  def dspPoke(bundle: Data, value: Double): Unit = {
    bundle match {
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
  //scalastyle:on cyclomatic.complexity

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
    //scalastyle:off regex
    if (_verbose) {
      println(s"DspPoke($c, $value)")
    }
    //scalastyle:on regex
  }

  // [stevo]: poke a value in type typ to a UInt input
  // it's okay if typ has smaller underlying width than the bundle; we assume it just zero-pads
  //scalastyle:off cyclomatic.complexity
  def dspPokeAs[U<:Data](bundle: Data, value: Double, typ: U): Unit = {
    bundle match {
      case u: UInt =>
        typ match {
          case s: SInt =>
            assert(u.getWidth >= s.getWidth,
              s"Error: pokeAs($bundle, $value, $typ): $typ has smaller underlying width than $bundle")
            val a: BigInt = BigInt(value.round.toInt)
            poke(u, a)
          case f: FixedPoint =>
            f.binaryPoint match {
              case KnownBinaryPoint(binaryPoint) =>
                assert(u.getWidth >= f.getWidth,
                  s"Error: pokeAs($bundle, $value, $typ): $typ has smaller underlying width than $bundle")
                // [stevo]: convert negative to two's complement positive
                val bigInt = toBigIntUnsigned(value, f.getWidth, binaryPoint)
                poke(u, bigInt)
              case _ =>
                throw DspException(
                  s"Error: pokeAs($bundle, $value, $typ): Can't create FixedPoint for $value, from signal template $typ")
            }
          case r: DspReal =>
            assert(u.getWidth >= r.getWidth,
              s"Error: pokeAs($bundle, $value, $typ): $typ has smaller underlying width than $bundle")
            poke(u, doubleToBigIntBits(value))
          case c: DspComplex[_]  => c.underlyingType() match {
            case "fixed" => poke(c.real.asInstanceOf[FixedPoint], value)
            case "real"  => dspPoke(c.real.asInstanceOf[DspReal], value)
            case "SInt" => poke(c.real.asInstanceOf[SInt], value.toInt)
            case _ =>
              throw DspException(
                s"pokeAs($bundle, $value, $typ): bundle DspComplex has unknown underlying type ${typ.getClass.getName}")
          }
          case _ =>
            throw DspException(s"pokeAs($bundle, $value, $typ): typ has unknown type ${typ.getClass.getName}")
        }
      case _ =>
        throw DspException(s"pokeAs($bundle, $value, $typ): bundle should be type UInt but is ${bundle.getClass.getName}")
    }
    //scalastyle:off regex
    if (_verbose) {
      println(s"pokeAs($bundle, $value, $typ)")
    }
    //scalastyle:on regex
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

  // [stevo]: peek a UInt but cast it to another type
  def dspPeekAs[U<:Data](data: Data, typ: U): Either[Double, Complex] = {
    data match {
      case u: UInt =>
        typ match {
          // TODO:
          //case c: DspComplex[_] =>
          //  c.underlyingType() match {
          //    case "fixed" =>
          //      val real      = dspPeek(c.real.asInstanceOf[FixedPoint]).left.get
          //      val imaginary = dspPeek(c.imaginary.asInstanceOf[FixedPoint]).left.get
          //      Right(Complex(real, imaginary))
          //    case "real"  =>
          //      val bigIntReal      = dspPeek(c.real.asInstanceOf[DspReal]).left.get
          //      val bigIntImaginary = dspPeek(c.imaginary.asInstanceOf[DspReal]).left.get
          //      Right(Complex(bigIntReal, bigIntImaginary))
          //    case "SInt" =>
          //      val real = peek(c.real.asInstanceOf[SInt]).toDouble
          //      val imag = peek(c.imaginary.asInstanceOf[SInt]).toDouble
          //      Right(Complex(real, imag))
          //    case _ =>
          //      throw DspException(
          //        s"peek($c): c DspComplex has unknown underlying type ${c.getClass.getName}")
          //  }
          case _: DspReal =>
            val bigInt = peek(u)
            Left(bigIntBitsToDouble(bigInt))
          case r: FixedPoint =>
            val bigInt = peek(u)
            Left(toDoubleFromUnsigned(bigInt, r.getWidth, r.binaryPoint.get))
          // TODO:
          //case s: SInt =>
          //  Left(peek(s).toDouble)
          case _ =>
            throw DspException(s"peek($data): data has unknown type ${data.getClass.getName}")

      }
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
    val bigInt = peek(signal.asInstanceOf[Bits])
    signal.binaryPoint match {
      case KnownBinaryPoint(binaryPoint) =>
        val double = FixedPoint.toDouble(bigInt, binaryPoint)
        double
      case _ =>
        throw DspException(s"Error: peek: Can't peek a FixedPoint, from signal template $signal")
    }
  }

  def nearlyEqual(a: Double, b: Double): Boolean = {
    val epsilon = 1e-12
    val divisor: Double = if(b.abs < epsilon) epsilon else b.abs
    val result = (a - b).abs / (divisor + epsilon) < epsilon
    //if(!result) {
    //  println(f"Got error on a $a vs $b  ${a-b}%20.16f")
    //}
    result
  }

  //scalastyle:off regex
  def dspExpect(data: Data, expected: Double, msg: String): Unit = {
    dspPeek(data) match {
      case Left(double) =>
        println(f"expect got $double%15.8f expect $expected%15.8f")
        expect(nearlyEqual(double, expected), msg)
      case Right(complex) =>
        throw DspException(s"dspExpect($data, $expected) returned $complex when expecting double")
    }
  }

  def dspExpect(data: DspComplex[_], expected: Complex, msg: String): Unit = {
    dspPeek(data) match {
      case Right(complex) =>
        println(f"expect got $complex expect $expected")
        expect(nearlyEqual(complex.real, expected.real), msg)
        expect(nearlyEqual(complex.imag, expected.imag), msg)
      case Left(double) =>
        throw DspException(s"dspExpect($data, $expected) returned $double when expecting complex")
    }
  }
  //scalastyle:on regex
}
