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
import dsptools.Utilities._
import scala.math.{abs, pow}
import _root_.junctions._
import sam._
import testchipip._

trait InputTester {
  var streamInValid: Boolean = true
  def pauseStream(): Unit = streamInValid = false
  def playStream():  Unit = streamInValid = true
  def restartStream(): Unit = streamInIter = streamIn.iterator

  def streamIn: Seq[BigInt]
  protected var streamInIter = streamIn.iterator
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
              val new_bi = dsptools.Utilities.toBigIntUnsigned(dbl, f.getWidth, binaryPoint)
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
          (((bi << gen.real.getWidth) + new_bi_real) << gen.imaginary.getWidth) + new_bi_imag
        })
      case "fixed" =>
        gen.real.asInstanceOf[FixedPoint].binaryPoint match {
          case KnownBinaryPoint(binaryPoint) =>
            in.map(x => x.reverse.foldLeft(BigInt(0)) { case (bi, cpx) =>
              val new_bi_real = toBigIntUnsigned(cpx.real, gen.real.getWidth, binaryPoint)
              val new_bi_imag = toBigIntUnsigned(cpx.imag, gen.real.getWidth, binaryPoint)
              (((bi << gen.real.getWidth) + new_bi_real) << gen.imaginary.getWidth) + new_bi_imag
            })
          case _ =>
            throw DspException(s"Error: packInput: Can't create Complex[FixedPoint] from signal template ${gen.getClass.getName}")
        }
      case "real" =>
        in.map(x => x.reverse.foldLeft(BigInt(0)) { case (bi, cpx) =>
          val new_bi_real = doubleToBigIntBits(cpx.real)
          val new_bi_imag = doubleToBigIntBits(cpx.imag)
          (((bi << gen.real.getWidth) + new_bi_real) << gen.imaginary.getWidth) + new_bi_imag
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
      poke(in.valid, 1)
      poke(in.bits, streamInIter.next)
    } else {
      poke(in.valid, 0)
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
          val imag = (x >> ((gen.real.getWidth + gen.imaginary.getWidth) * idx)) % pow(2, gen.imaginary.getWidth).toInt
          val real = (x >> ((gen.real.getWidth + gen.imaginary.getWidth) * idx + gen.imaginary.getWidth)) % pow(2, gen.real.getWidth).toInt
          Complex(real.toDouble, imag.toDouble)
        }}).flatten.toSeq
      case "fixed" =>
        gen.real.asInstanceOf[FixedPoint].binaryPoint match {
          case KnownBinaryPoint(binaryPoint) =>
            streamOut.last.map(x => (0 until lanesOut).map{ idx => {
              val imag = (x >> ((gen.real.getWidth + gen.imaginary.getWidth) * idx)) % pow(2, gen.imaginary.getWidth).toInt
              val real = (x >> ((gen.real.getWidth + gen.imaginary.getWidth) * idx + gen.imaginary.getWidth)) % pow(2, gen.real.getWidth).toInt
              Complex(toDoubleFromUnsigned(real, gen.real.getWidth, binaryPoint), toDoubleFromUnsigned(imag, gen.imaginary.getWidth, binaryPoint))
            }}).flatten.toSeq
          case _ =>
            throw DspException(s"Error: packInput: Can't create FixedPoint from signal template ${gen.getClass.getName}")
        }
      case "real" =>
        streamOut.last.map(x => (0 until lanesOut).map{ idx => {
          // [stevo]: comes out as (imaginary, real) because it's alphabetical
          val imag = (x >> ((gen.real.getWidth + gen.imaginary.getWidth) * idx))
          val real = (x >> ((gen.real.getWidth + gen.imaginary.getWidth) * idx + gen.imaginary.getWidth))
          Complex(bigIntBitsToDouble(real), bigIntBitsToDouble(imag))
        }}).flatten.toSeq
      case _ =>
        throw DspException(s"Error: packInput: DspComplex has unknown underlying type ${gen.getClass.getName}")
    }
  }
}

trait StreamOutputTester[T <: DspBlock] extends OutputTester { this: DspTester[T] =>
  def dut: T
  def outputStep: Unit = {
    if (peek(dut.io.out.sync) != BigInt(0)) {
      streamOut_ += new scala.collection.mutable.Queue[BigInt]()
    }
    if (peek(dut.io.out.valid) != BigInt(0) && !streamOut_.isEmpty) {
      streamOut_.last += peek(dut.io.out.bits)
    }
  }
}

trait AXIOutputTester[T <: Module] extends OutputTester with AXIRWTester[T] with InputTester { this: DspTester[T] =>
  // don't begin streaming until SAM is ready
  streamInValid = false
  var axi: NastiIO = ctrlAXI
  var wordsDumped: Int = 0

  def lazySam: LazySAM
  def samSize = lazySam.module.sam.w
  // for now, only support streaming as much as the SAM can store
  require(streamIn.length <= samSize, "Can't stream in more than the SAM can capture")
  def addrmap = SCRAddressMap(lazySam.scrbuilder.devName).get
  def ctrlAXI: NastiIO
  def dataAXI: NastiIO
  def outputStep: Unit = {}

  override def playStream(): Unit = {
    initiateSamCapture(streamIn.length)
    super[InputTester].playStream()
  }

  def initiateSamCapture(nSamps: Int, waitForSync: Boolean = false): Unit = {
    val oldAXI = axi
    axi = ctrlAXI

    val samWStartAddr = addrmap("samWStartAddr")
    val samWTargetCount = addrmap("samWTargetCount")
    val samWTrig = addrmap("samWTrig")
    val samWWaitForSync = addrmap("samWWaitForSync")

    axiWrite(samWStartAddr, 0)
    axiWrite(samWTargetCount, nSamps)
    axiWrite(samWWaitForSync, waitForSync)
    axiWrite(samWTrig, 1)

    axi = oldAXI
  }

  def getOutputFromSam(): Seq[BigInt] = {
    val oldAXI = axi
    axi = ctrlAXI

    val samWWriteCount = addrmap("samWWriteCount")
    val samWPacketCount = addrmap("samWPacketCount")
    val samWSyncAddr = addrmap("samWSyncAddr")

    val base = axiRead(samWSyncAddr)
    val writeCount = axiRead(samWWriteCount)

    axi = dataAXI

    val readout = (base + wordsDumped until writeCount).map(addr => {
      axiRead(addr)
    })

    streamOut_.last ++= readout

    axi = oldAXI

    readout
  }


}

trait StreamIOTester[T <: DspBlock] extends StreamInputTester[T] with StreamOutputTester[T] {
  this: DspTester[T] =>
}

trait AXIRWTester[T <: Module] { this: DspTester[T] =>

  def axi: NastiIO
  def maxWait = 100

  def aw_ready: Boolean = { (peek(axi.aw.ready) != BigInt(0)) }
  def w_ready: Boolean = { (peek(axi.w.ready) != BigInt(0)) }
  def b_ready: Boolean = { (peek(axi.b.valid) != BigInt(0)) }
  def ar_ready: Boolean = { (peek(axi.ar.ready) != BigInt(0)) }
  def r_ready: Boolean = { (peek(axi.r.valid) != BigInt(0)) }

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

abstract class DspBlockTester[V <: DspBlock](dut: V, override val maxWait: Int = 100)
  extends DspTester[V](dut) with StreamIOTester[V] with AXIRWTester[V] {
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

class DspChainTester[V <: DspChain](dut: V) extends DspTester[V](dut) with StreamInputTester[V] with AXIRWTester[V] with AXIOutputTester[V] {
  def in = dut.mod_ios.head.in
  def ctrlAXI = dut.io.control_axi
  def dataAXI = dut.io.data_axi
  def lazySam = dut.lazySams.last
  def streamIn = (0 until 12).map(x => BigInt(x)).toSeq
}

