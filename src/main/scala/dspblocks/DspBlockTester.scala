// See LICENSE for license details

package dspblocks

import breeze.math.Complex
import cde._
import chisel3._
import chisel3.experimental._
import chisel3.iotesters.PeekPokeTester
import chisel3.internal.firrtl.KnownBinaryPoint
import chisel3.util.log2Up
import dsptools.{DspTester, DspException}
import dsptools.numbers.{DspComplex, DspReal}
import dsptools.DspTesterUtilities._
import scala.math.{abs, pow}
import _root_.junctions._
import sam._
import testchipip._

trait HasDspPokeAs[T <: Module] { this: DspTester[T] =>
  def dspPeek(data: Data): Either[Double, Complex] = {
    data match {
      case c: DspComplex[_] =>
        c.underlyingType() match {
          case "fixed" =>
            val real      = dspPeek(c.real.asInstanceOf[FixedPoint]).left.get
            val imaginary = dspPeek(c.imag.asInstanceOf[FixedPoint]).left.get
            Right(Complex(real, imaginary))
          case "real"  =>
            val bigIntReal      = dspPeek(c.real.asInstanceOf[DspReal]).left.get
            val bigIntImaginary = dspPeek(c.imag.asInstanceOf[DspReal]).left.get
            Right(Complex(bigIntReal, bigIntImaginary))
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
          // poke double into complex as just the real component
          case c: DspComplex[x] => dspPokeAs(bundle, Complex(value, 0), typ.asInstanceOf[DspComplex[x]])
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

  // [stevo]: poke a complex value in type typ to a UInt input
  // it's okay if typ has smaller underlying width than the bundle; we assume it just zero-pads
  //scalastyle:off cyclomatic.complexity
  def dspPokeAs[U<:Data](bundle: Data, value: Complex, typ: DspComplex[U]): Unit = {
    bundle match {
      case u: UInt =>
        typ.underlyingType() match {
          case "SInt" =>
            assert(u.getWidth >= typ.real.asInstanceOf[SInt].getWidth*2,
              s"Error: pokeAs($bundle, $value, $typ): $typ has smaller underlying width than $bundle")
            val a: BigInt = BigInt(value.real.round.toInt)
            val b: BigInt = BigInt(value.imag.round.toInt)
            poke(u, (a << typ.real.asInstanceOf[SInt].getWidth) + b)
          case "fixed" =>
            typ.real.asInstanceOf[FixedPoint].binaryPoint match {
              case KnownBinaryPoint(binaryPoint) =>
                assert(u.getWidth >= typ.real.asInstanceOf[FixedPoint].getWidth*2,
                  s"Error: pokeAs($bundle, $value, $typ): $typ has smaller underlying width than $bundle")
                // [stevo]: convert negative to two's complement positive
                val a = toBigIntUnsigned(value.real, typ.real.asInstanceOf[FixedPoint].getWidth, binaryPoint)
                val b = toBigIntUnsigned(value.imag, typ.imag.asInstanceOf[FixedPoint].getWidth, binaryPoint)
                poke(u, (a << typ.real.asInstanceOf[FixedPoint].getWidth) + b)
              case _ =>
                throw DspException(
                  s"Error: pokeAs($bundle, $value, $typ): Can't create FixedPoint for $value, from signal template $typ")
            }
          case "real" =>
            assert(u.getWidth >= typ.real.asInstanceOf[DspReal].getWidth*2,
              s"Error: pokeAs($bundle, $value, $typ): $typ has smaller underlying width than $bundle")
            val a = doubleToBigIntBits(value.real)
            val b = doubleToBigIntBits(value.imag)
            poke(u, (a << typ.real.asInstanceOf[DspReal].getWidth) + b)
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

}

trait InputTester {
  var streamInValid: Boolean = false
  def pauseStream(): Unit = streamInValid = false
  def playStream():  Unit = streamInValid = true

  def streamIn: Seq[Seq[BigInt]]
  def streamInFlat = streamIn.flatten.toSeq
  def syncIn = streamIn.map(s =>
      if (s.size > 0)
        Seq(true) ++ Seq.fill(s.size - 1)(false)
      else Seq()
      ).flatten.toSeq
  protected lazy val streamInIter = streamInFlat.zip(syncIn).iterator
  def done = !streamInIter.hasNext
  def inputStep: Unit
  // handle normal input types
  def packInputStream[T<:Data](in: Seq[Seq[Double]], gen: T): Seq[BigInt] = {
    gen match {
      case s: SInt =>
        in.map(x => x.reverse.foldLeft(BigInt(0)) { case (bi, dbl) =>
          val new_bi = BigInt(dbl.round.toInt)
          (bi << gen.getWidth) + new_bi
        })
      case f: FixedPoint =>
        f.asInstanceOf[FixedPoint].binaryPoint match {
          case KnownBinaryPoint(binaryPoint) =>
            in.map(x => x.reverse.foldLeft(BigInt(0)) { case (bi, dbl) =>
              val new_bi = toBigIntUnsigned(dbl, f.getWidth, binaryPoint)
              (bi << gen.getWidth) + new_bi
            })
          case _ =>
            throw DspException(s"Error: packInput: Can't create FixedPoint from signal template $f")
        }
      case r: DspReal =>
        in.map(x => x.reverse.foldLeft(BigInt(0)) { case (bi, dbl) =>
          val new_bi = doubleToBigIntBits(dbl)
          (bi << gen.getWidth) + new_bi
        })
      case _ =>
        throw DspException(s"Error: packInput: Can't pack input type $gen yet...")
    }
  }

  // handle complex input
  def packInputStream[T<:Data](in: Seq[Seq[Complex]], gen: DspComplex[T]): Seq[BigInt] = {
    gen.underlyingType() match {
      case "SInt" =>
        in.map(x => x.reverse.foldLeft(BigInt(0)) { case (bi, cpx) =>
          val new_bi_real = BigInt(cpx.real.round.toInt)
          val new_bi_imag = BigInt(cpx.imag.round.toInt)
          (((bi << gen.real.getWidth) + new_bi_real) << gen.imag.getWidth) + new_bi_imag
        })
      case "fixed" =>
        gen.real.asInstanceOf[FixedPoint].binaryPoint match {
          case KnownBinaryPoint(binaryPoint) =>
            in.map(x => x.reverse.foldLeft(BigInt(0)) { case (bi, cpx) =>
              val new_bi_real = toBigIntUnsigned(cpx.real, gen.real.getWidth, binaryPoint)
              val new_bi_imag = toBigIntUnsigned(cpx.imag, gen.real.getWidth, binaryPoint)
              (((bi << gen.real.getWidth) + new_bi_real) << gen.imag.getWidth) + new_bi_imag
            })
          case _ =>
            throw DspException(s"Error: packInput: Can't create Complex[FixedPoint] from signal template ${gen.getClass.getName}")
        }
      case "real" =>
        in.map(x => x.reverse.foldLeft(BigInt(0)) { case (bi, cpx) =>
          val new_bi_real = doubleToBigIntBits(cpx.real)
          val new_bi_imag = doubleToBigIntBits(cpx.imag)
          (((bi << gen.real.getWidth) + new_bi_real) << gen.imag.getWidth) + new_bi_imag
        })
      case _ =>
        throw DspException(s"Error: packInput: DspComplex has unknown underlying type ${gen.getClass.getName}")
    }
  }
}

trait StreamInputTester[T <: Module] extends InputTester { this: DspTester[T] =>
  def dut: T
  def in: dspjunctions.ValidWithSync[UInt]
  def inputStep: Unit = {
    if (streamInValid && streamInIter.hasNext) {
      val next = streamInIter.next
      println(s"Called next, got $next")
      poke(in.valid, 1)
      poke(in.bits, next._1)
      poke(in.sync, next._2)
    } else {
      poke(in.valid, 0)
      poke(in.sync,  0)
    }
  }
}

trait OutputTester {
  def outputStep: Unit
  protected val streamOut_ = new scala.collection.mutable.Queue[scala.collection.mutable.Queue[BigInt]]
  streamOut_ += new scala.collection.mutable.Queue[BigInt]() // add an initial queue in case sync never goes high
  val streamOut: Seq[Seq[BigInt]] = streamOut_
  // unpack normal output data types
  def unpackOutputStream[T<:Data](gen: T, lanesOut: Int): Seq[Double] = {
    gen match {
      case s: SInt =>
        streamOut.last.map(x => (0 until lanesOut).map{ idx => {
          // TODO: doesn't work if width is > 32
          ((x >> (gen.getWidth * idx)) % pow(2, gen.getWidth).toInt).toDouble
        }}).flatten.toSeq
      case f: FixedPoint =>
        f.asInstanceOf[FixedPoint].binaryPoint match {
          case KnownBinaryPoint(binaryPoint) =>
            streamOut.last.map(x => (0 until lanesOut).map{ idx => {
              // TODO: doesn't work if width is > 32
              val y = (x >> (gen.getWidth * idx)) % pow(2, gen.getWidth).toInt
              toDoubleFromUnsigned(y, gen.getWidth, binaryPoint)
            }}).flatten.toSeq
          case _ =>
            throw DspException(s"Error: packInput: Can't create FixedPoint from signal template $f")
        }
      case r: DspReal =>
        streamOut.last.map(x => (0 until lanesOut).map{ idx => {
          val y = (x >> (gen.getWidth * idx))
          bigIntBitsToDouble(y)
        }}).flatten.toSeq
      case _ =>
        throw DspException(s"Error: packInput: Can't unpack output type $gen yet...")
    }
  }

  // unpack complex output data
  def unpackOutputStream[T<:Data](gen: DspComplex[T], lanesOut: Int): Seq[Complex] = {
    gen.underlyingType() match {
      case "SInt" =>
        streamOut.last.map(x => (0 until lanesOut).map{ idx => {
          // TODO: doesn't work if width is > 32
          val imag = (x >> ((gen.real.getWidth + gen.imag.getWidth) * idx)) % pow(2, gen.imag.getWidth).toInt
          val real = (x >> ((gen.real.getWidth + gen.imag.getWidth) * idx + gen.imag.getWidth)) % pow(2, gen.real.getWidth).toInt
          Complex(real.toDouble, imag.toDouble)
        }}).flatten.toSeq
      case "fixed" =>
        gen.real.asInstanceOf[FixedPoint].binaryPoint match {
          case KnownBinaryPoint(binaryPoint) =>
            streamOut.last.map(x => (0 until lanesOut).map{ idx => {
              val imag = (x >> ((gen.real.getWidth + gen.imag.getWidth) * idx)) % pow(2, gen.imag.getWidth).toInt
              val real = (x >> ((gen.real.getWidth + gen.imag.getWidth) * idx + gen.imag.getWidth)) % pow(2, gen.real.getWidth).toInt
              Complex(toDoubleFromUnsigned(real, gen.real.getWidth, binaryPoint), toDoubleFromUnsigned(imag, gen.imag.getWidth, binaryPoint))
            }}).flatten.toSeq
          case _ =>
            throw DspException(s"Error: packInput: Can't create FixedPoint from signal template ${gen.getClass.getName}")
        }
      case "real" =>
        streamOut.last.map(x => (0 until lanesOut).map{ idx => {
          // [stevo]: comes out as (imag, real) because it's alphabetical
          val imag = (x >> ((gen.real.getWidth + gen.imag.getWidth) * idx))
          val real = (x >> ((gen.real.getWidth + gen.imag.getWidth) * idx + gen.imag.getWidth))
          Complex(bigIntBitsToDouble(real), bigIntBitsToDouble(imag))
        }}).flatten.toSeq
      case _ =>
        throw DspException(s"Error: packInput: DspComplex has unknown underlying type ${gen.getClass.getName}")
    }
  }
}

trait StreamOutputTester[T <: DspBlockModule] extends OutputTester { this: DspTester[T] =>
  def dut: T
  def outputStep: Unit = {
    if (peek(dut.io.out.sync)) {
      streamOut_ += new scala.collection.mutable.Queue[BigInt]()
    }
    if (peek(dut.io.out.valid) && !streamOut_.isEmpty) {
      streamOut_.last += peek(dut.io.out.bits)
    }
  }
}

trait AXIOutputTester[T <: Module] extends OutputTester with InputTester { this: DspTester[T] with AXIRWTester[T] =>
  // don't begin streaming until SAM is ready
  streamInValid = false
  var axi: NastiIO = ctrlAXI
  var wordsDumped: Int = 0

  def flattenedLazySams: Seq[SAMWrapper]
  def samSize = flattenedLazySams.last.module.sam.w // TODO fix
  // for now, only support streaming as much as the SAM can store
  def addrmap = testchipip.SCRAddressMap.contents.map({ case (mod, map) =>
    map.map { case(key, value) => (s"$mod:$key", value) }
  }).reduce(_++_) ++ flattenedLazySams.map(s =>
    s"${s.id}:data" -> s.dataBaseAddr
    )
  def ctrlAXI: NastiIO
  def dataAXI: NastiIO
  def outputStep: Unit = {}

  override def playStream(): Unit = {
    // initiateSamCapture(streamIn.map(_.length).reduce(_+_))
    super[InputTester].playStream()
  }

  // use given SAM by default
  private def getPrefix(prefix: Option[String]) =
    prefix.getOrElse(flattenedLazySams.last.id)

  def initiateSamCapture(nSamps: Int, waitForSync: Boolean = false,
      prefix: Option[String] = None, checkWrites: Boolean = true): Unit = {
    require(streamIn.length <= samSize, "Can't stream in more than the SAM can capture")

    val _prefix = getPrefix(prefix)

    val oldAXI = axi
    axi = ctrlAXI

    val samWStartAddr = addrmap(s"${_prefix}:samWStartAddr")
    val samWTargetCount = addrmap(s"${_prefix}:samWTargetCount")
    val samWTrig = addrmap(s"${_prefix}:samWTrig")
    val samWWaitForSync = addrmap(s"${_prefix}:samWWaitForSync")

    axiWrite(samWStartAddr, 0)
    if (checkWrites) {
      val wStartAddrRead = axiRead(samWStartAddr)
      require(wStartAddrRead == BigInt(0), s"WStartAddr wrong, was $wStartAddrRead should be 0")
    }

    axiWrite(samWTargetCount, nSamps)
    if (checkWrites) {
      val wTargetCountRead = axiRead(samWTargetCount)
      require(wTargetCountRead == BigInt(nSamps), s"WTargetCount wrong, was $wTargetCountRead should be $nSamps")
    }

    axiWrite(samWWaitForSync, waitForSync)
    if (checkWrites) {
      val wWaitForSyncRead = axiRead(samWWaitForSync)
      require(wWaitForSyncRead == waitForSync.toInt, s"WaitForSync wrong, was $wWaitForSyncRead should be $waitForSync")
    }

    axiWrite(samWTrig, 1)
    if (checkWrites) {
      val wTrigRead = axiRead(samWTrig)
      require(wTrigRead == 1, s"TrigRead wrong, was $wTrigRead should be 1")
    }

    axi = oldAXI
  }

  def getOutputFromSam(prefix: Option[String] = None): Seq[BigInt] = {
    val _prefix = getPrefix(prefix)
    val oldAXI = axi
    axi = ctrlAXI

    val samWWriteCount = addrmap(s"${_prefix}:samWWriteCount")
    val samWPacketCount = addrmap(s"${_prefix}:samWPacketCount")
    val samWSyncAddr = addrmap(s"${_prefix}:samWSyncAddr")

    val samBase = addrmap(s"${_prefix}:data")
    val base = samBase + axiRead(samWSyncAddr)
    println(s"SyncAddr is ${base}")
    val writeCount = axiRead(samWWriteCount)
    val packetCount = axiRead(samWPacketCount)

    println(s"Reading $writeCount words from $base")

    axi = dataAXI

    val readout = //(base + wordsDumped until writeCount).map(addr => {
      (0 until writeCount.toInt).map(addr => {
      axiRead(base + addr * 8)
    })

    streamOut_.last ++= readout

    axi = oldAXI

    println(s"Read out $readout")

    readout
  }


}

trait StreamIOTester[T <: DspBlockModule] extends StreamInputTester[T] with StreamOutputTester[T] {
  this: DspTester[T] =>
}

trait AXIRWTester[T <: Module] { this: DspTester[T] with HasDspPokeAs[T] =>

  def axi: NastiIO
  def maxWait = 100

  def aw_ready: Boolean = { (peek(axi.aw.ready)) }
  def w_ready: Boolean = { (peek(axi.w.ready)) }
  def b_ready: Boolean = { (peek(axi.b.valid)) }
  def ar_ready: Boolean = { (peek(axi.ar.ready)) }
  def r_ready: Boolean = { (peek(axi.r.valid)) }

  poke(axi.aw.valid, 0)
  poke(axi.ar.valid, 0)
  poke(axi.b.ready,  0)
  poke(axi.ar.valid, 0)
  poke(axi.r.ready, 0)

  def axiDataWidth = axi.w.bits.data.getWidth
  def axiDataBytes = axiDataWidth / 8
  def burstLen = axiDataBytes
  def axiWrite(addr: BigInt, value: BigInt): Unit = {

    // s_write_addr
    poke(axi.aw.valid, 1)
    poke(axi.aw.bits.id, 0)
    poke(axi.aw.bits.user, 0)
    poke(axi.aw.bits.addr, addr)
    poke(axi.aw.bits.len, 0)
    poke(axi.aw.bits.size, log2Up(axiDataBytes))
    poke(axi.aw.bits.lock, 0)
    poke(axi.aw.bits.cache, 0)
    poke(axi.aw.bits.prot, 0)
    poke(axi.aw.bits.qos, 0)
    poke(axi.aw.bits.region, 0)

    // s_write_data
    poke(axi.w.valid, 1)
    poke(axi.w.bits.data, value)
    poke(axi.w.bits.strb, 0xFF)
    poke(axi.w.bits.last, 1)
    poke(axi.w.bits.id, 0)
    poke(axi.w.bits.user, 0)

    var waited = 0
    var a_written = false
    var d_written = false
    while (!a_written || !d_written) {
      // check for ready condition
      if (!a_written) { a_written = aw_ready }
      if (!d_written) { d_written = w_ready }
      // if (waited >= maxWait) return BigInt(-1)
      require(waited < maxWait, "Timeout waiting for AXI AW or W to be ready")
      step(1)
      // invalidate when values are received
      if (a_written) { poke(axi.aw.valid, 0) }
      if (d_written) { poke(axi.w.valid, 0); poke(axi.w.bits.last, 0) }
      waited += 1
    }

    // s_write_stall

    waited = 0
    do {
      require(waited < maxWait, "Timeout waiting for AXI B to be valid")
      step(1)
      waited += 1
    } while (!b_ready);

    // s_write_resp
    poke(axi.b.ready, 1)
    step(1)
    poke(axi.b.ready, 0)
  }
  def axiWrite(addr: Int, value: Int): Unit = axiWrite(BigInt(addr), BigInt(value))
  def axiWrite(addr: BigInt, value: Int): Unit = axiWrite(addr, BigInt(value))
  def axiWrite(addr: Int, value: BigInt): Unit = axiWrite(BigInt(addr), value)

  def axiWriteAs[T<:Data](addr: BigInt, value: Double, typ: T): Unit = {

    // s_write_addr
    poke(axi.aw.valid, 1)
    poke(axi.aw.bits.id, 0)
    poke(axi.aw.bits.user, 0)
    poke(axi.aw.bits.addr, addr)
    poke(axi.aw.bits.len, 0)
    poke(axi.aw.bits.size, log2Up(axiDataBytes))
    poke(axi.aw.bits.lock, 0)
    poke(axi.aw.bits.cache, 0)
    poke(axi.aw.bits.prot, 0)
    poke(axi.aw.bits.qos, 0)
    poke(axi.aw.bits.region, 0)

    // s_write_data
    poke(axi.w.valid, 1)
    dspPokeAs(axi.w.bits.data, value, typ)
    poke(axi.w.bits.strb, 0xFF)
    poke(axi.w.bits.last, 1)
    poke(axi.w.bits.id, 0)
    poke(axi.w.bits.user, 0)

    var waited = 0
    var a_written = false
    var d_written = false
    while (!a_written || !d_written) {
      // check for ready condition
      if (!a_written) { a_written = aw_ready }
      if (!d_written) { d_written = w_ready }
      require(waited < maxWait, "Timeout waiting for AXI AW or W to be ready")
      step(1)
      // invalidate when values are received
      if (a_written) { poke(axi.aw.valid, 0) }
      if (d_written) { poke(axi.w.valid, 0); poke(axi.w.bits.last, 0) }
      waited += 1
    }

    // s_write_stall

    waited = 0
    do {
      require(waited < maxWait, "Timeout waiting for AXI B to be valid")
      step(1)
      waited += 1
    } while (!b_ready);

    // s_write_resp
    poke(axi.b.ready, 1)
    step(1)
    poke(axi.b.ready, 0)
  }
  def axiWriteAs[T<:Data](addr: Int, value: Double, typ: T): Unit = axiWriteAs(BigInt(addr), value, typ)

  // TODO: make this not copy pasta
  def axiWriteAs[T<:Data](addr: BigInt, value: Complex, typ: DspComplex[T]): Unit =  {
    // s_write_addr
    poke(axi.aw.valid, 1)
    poke(axi.aw.bits.id, 0)
    poke(axi.aw.bits.user, 0)
    poke(axi.aw.bits.addr, addr)
    poke(axi.aw.bits.len, 0)
    poke(axi.aw.bits.size, log2Up(axiDataBytes))
    poke(axi.aw.bits.lock, 0)
    poke(axi.aw.bits.cache, 0)
    poke(axi.aw.bits.prot, 0)
    poke(axi.aw.bits.qos, 0)
    poke(axi.aw.bits.region, 0)

    // s_write_data
    poke(axi.w.valid, 1)
    dspPokeAs(axi.w.bits.data, value, typ)
    poke(axi.w.bits.strb, 0xFF)
    poke(axi.w.bits.last, 1)
    poke(axi.w.bits.id, 0)
    poke(axi.w.bits.user, 0)

    var waited = 0
    var a_written = false
    var d_written = false
    while (!a_written || !d_written) {
      // check for ready condition
      if (!a_written) { a_written = aw_ready }
      if (!d_written) { d_written = w_ready }
      require(waited < maxWait, "Timeout waiting for AXI AW or W to be ready")
      step(1)
      // invalidate when values are received
      if (a_written) { poke(axi.aw.valid, 0) }
      if (d_written) { poke(axi.w.valid, 0); poke(axi.w.bits.last, 0) }
      waited += 1
    }

    // s_write_stall

    waited = 0
    do {
      require(waited < maxWait, "Timeout waiting for AXI B to be valid")
      step(1)
      waited += 1
    } while (!b_ready);

    // s_write_resp
    poke(axi.b.ready, 1)
    step(1)
    poke(axi.b.ready, 0)
  }
  def axiWriteAs[T<:Data](addr: Int, value: Complex, typ: DspComplex[T]): Unit =  axiWriteAs(BigInt(addr), value, typ)

  def axiRead(addr: BigInt): BigInt = {

    // s_read_addr
    poke(axi.ar.valid, 1)
    poke(axi.ar.bits.id, 0)
    poke(axi.ar.bits.user, 0)
    poke(axi.ar.bits.addr, addr)
    poke(axi.ar.bits.len, 0)
    poke(axi.ar.bits.size, log2Up(axiDataBytes))
    poke(axi.ar.bits.lock, 0)
    poke(axi.ar.bits.cache, 0)
    poke(axi.ar.bits.prot, 0)
    poke(axi.ar.bits.qos, 0)
    poke(axi.ar.bits.region, 0)

    var waited = 0
    while (!ar_ready) {
      require(waited < maxWait, "Timeout waiting for AXI AR to be ready")
      step(1)
      waited += 1
    }

    step(1)
    poke(axi.ar.valid, 0)
    step(1)
    poke(axi.r.ready, 1)

    // s_read_data
    while (!r_ready) {
      // if (waited >= maxWait) return BigInt(-1)
      require(waited < maxWait, "Timeout waiting for AXI R to be valid")
      step(1)
      waited += 1
    }

    val ret = peek(axi.r.bits.data)
    step(1)
    poke(axi.r.ready, 0)
    step(1)
    ret
  }
  def axiRead(addr: Int): BigInt = axiRead(BigInt(addr))
}

abstract class DspBlockTester[V <: DspBlockModule](dut: V, override val maxWait: Int = 100)
  extends DspTester[V](dut) with HasDspPokeAs[V] with StreamIOTester[V] with AXIRWTester[V] {
  def in = dut.io.in
  def axi = dut.io.axi

  def addrmap = dut.addrmap //SCRAddressMap(dut.outer.scrbuilder.devName).get

  override def step(n: Int): Unit = {
    inputStep
    outputStep
    super.step(1)
    if (n > 1) step(n - 1)
  }

  // compares chisel and reference outputs, errors if they differ by more than epsilon
  def compareOutput(chisel: Seq[Double], ref: Seq[Double], epsilon: Double = 1e-12): Unit = {
    chisel.zip(ref).zipWithIndex.foreach { case((c, r), index) =>
      if (c != r) {
        val err = abs(c-r)/(abs(r)+epsilon)
        assert(err < epsilon, s"Error: mismatch on output $index of ${err*100}%\n\tReference: $r\n\tChisel:    $c")
      }
    }
  }

  // compares chisel and reference outputs, errors if they differ by more than epsilon
  def compareOutputComplex(chisel: Seq[Complex], ref: Seq[Complex], epsilon: Double = 1e-12): Unit = {
    chisel.zip(ref).zipWithIndex.foreach { case((c, r), index) =>
      if (c.real != r.real) {
        val err = abs(c.real-r.real)/(abs(r.real)+epsilon)
        assert(err < epsilon, s"Error: mismatch in real value on output $index of ${err*100}%\n\tReference: ${r.real}\n\tChisel:    ${c.real}")
      }
      if (c.imag != r.imag) {
        val err = abs(c.imag-r.imag)/(abs(r.imag)+epsilon)
        assert(err < epsilon, s"Error: mismatch in imag value on output $index of ${err*100}%\n\tReference: ${r.imag}\n\tChisel:    ${c.imag}")
      }
    }
  }
}

abstract class DspChainTester[V <: DspChainModule with AXI4SInputModule](dut: V) extends DspTester[V](dut) with AXIOutputTester[V] with StreamInputTester[V] with AXIRWTester[V] with HasDspPokeAs[V] {
  def in = dut.io.asInstanceOf[AXI4SInputIO].stream_in
  def ctrlAXI = dut.io.control_axi
  def dataAXI = dut.io.data_axi
  def flattenedLazySams = dut.flattenedLazySams

  override def step(n: Int): Unit = {
    inputStep
    outputStep
    super.step(1)
    if (n > 1) step(n - 1)
  }
}

