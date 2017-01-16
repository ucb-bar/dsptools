// See LICENSE for license details

package dsptools

import chisel3._
import breeze.math.Complex
import chisel3.util.log2Up
import chisel3.internal.throwException
import dsptools.junctions._
import cde._
// import junctions would import dsptools.junctions._
import _root_.junctions._
import uncore.tilelink._
import uncore.converters._
import rocketchip.PeripheryUtils
import chisel3.internal.firrtl.KnownBinaryPoint
import dsptools.numbers.{DspComplex, DspReal}
import testchipip._
import dsptools.Utilities._
import scala.math._

case object DspBlockKey extends Field[DspBlockParameters]

case class DspBlockParameters (
  inputWidth: Int,
  outputWidth: Int
)

trait HasDspBlockParameters {
  implicit val p: Parameters
  def inputWidth  = p(DspBlockKey).inputWidth
  def outputWidth = p(DspBlockKey).outputWidth
}

case object GenKey extends Field[GenParameters]

trait GenParameters {
  def genIn [T <: Data]: T
  def genOut[T <: Data]: T = genIn[T]
  def lanesIn: Int
  def lanesOut: Int = lanesIn
}

trait HasGenParameters[T <: Data, V <: Data] {
  implicit val p: Parameters
  def genExternal = p(GenKey)
  def genIn(dummy: Int = 0)  = genExternal.genIn[T]
  def genOut(dummy: Int = 0) = genExternal.genOut[V]
  def lanesIn  = genExternal.lanesIn
  def lanesOut = genExternal.lanesOut
  // todo some assertions that the width is correct
}

trait HasGenDspParameters[T <: Data, V <: Data] extends HasDspBlockParameters with HasGenParameters[T, V] {
  def portSize[U <: Data](lanes: Int, in: U): Int = {
    val unpadded = lanes * in.getWidth
    val topad = (8 - (unpadded % 8)) % 8
    unpadded + topad
  }
  abstract override def inputWidth     = portSize(lanesIn,  genIn())
  abstract override def outputWidth    = portSize(lanesOut, genOut())
}

trait DspBlockIO {
  def inputWidth: Int
  def outputWidth: Int
  implicit val p: Parameters

  val in  = Input( ValidWithSync(UInt(inputWidth.W)))
  val out = Output(ValidWithSync(UInt(outputWidth.W)))
  val axi = new NastiIO().flip
}

class BasicDspBlockIO()(implicit val p: Parameters) extends Bundle with HasDspBlockParameters with DspBlockIO {
  override def cloneType: this.type = new BasicDspBlockIO()(p).asInstanceOf[this.type]
}

abstract class DspBlock(b: => Option[Bundle with DspBlockIO] = None, override_clock: Option[Clock]=None, override_reset: Option[Bool]=None)
  (implicit val p: Parameters) extends Module(override_clock, override_reset) with HasDspBlockParameters {
  def baseAddr: BigInt
  val io: Bundle with DspBlockIO = IO(b.getOrElse(new BasicDspBlockIO))

  def unpackInput[T <: Data](lanes: Int, genIn: T) = {
    val i = Wire(ValidWithSync(Vec(lanes, genIn.cloneType)))
    i.valid := io.in.valid
    i.sync  := io.in.sync
    val w = i.bits.fromBits(io.in.bits)
    i.bits  := w
    i
  }
  def unpackOutput[T <: Data](lanes: Int, genOut: T) = {
    val o = Wire(ValidWithSync(Vec(lanes, genOut.cloneType)))
    io.out.valid := o.valid
    io.out.sync  := o.sync
    io.out.bits  := o.bits.asUInt
    o
  }

  var scrbuilt : Boolean = false
  val scrbuilder = new SCRBuilder(name)
  lazy val scr: SCRFile = {
    scrbuilt = true
    //val tl = Wire(new ClientUncachedTileLinkIO)
    val scr_ = scrbuilder.generate(baseAddr)
    //tl <> scr_.io.tl
    //PeripheryUtils.convertTLtoAXI(tl) <> io.axi
    val tl2axi = Module(new TileLinkIONastiIOConverter())
    tl2axi.io.tl <> scr_.io.tl
    io.axi <> tl2axi.io.nasti
    scr_
  }

  def addControl(name: String, init: UInt = null) = {
    require(!scrbuilt, 
      s"Called addControl after SCR has been built." + 
      s"Move your control() and status() calls after all addControl calls"
    )
    scrbuilder.addControl(name, init)
  }
  def addStatus(name: String) {
    require(!scrbuilt,
      s"Called addStatus after SCR has been built." + 
      s"Move your control() and status() calls after all addStatus calls"
    )
    scrbuilder.addStatus(name)
  }

  def control(name: String) = scr.control(name)
  def status(name : String) = scr.status(name)
}

class GenDspBlockIO[T <: Data, V <: Data]()(implicit val p: Parameters)
  extends Bundle with HasGenDspParameters[T, V] with DspBlockIO {
  override def cloneType = new GenDspBlockIO()(p).asInstanceOf[this.type]
}

abstract class GenDspBlock[T <: Data, V <: Data]
  (override_clock: Option[Clock]=None, override_reset: Option[Bool]=None)
  (implicit p: Parameters) extends DspBlock(Some(new GenDspBlockIO[T, V]), override_clock, override_reset)
  with HasGenDspParameters[T, V]

abstract class DspBlockTester[V <: DspBlock](dut: V, maxWait: Int = 100)(implicit p: Parameters)
  extends DspTester(dut) {
  var streamInValid: Boolean = true
  def pauseStream(): Unit = streamInValid = false
  def playStream():  Unit = streamInValid = true
  def streamIn: Seq[BigInt]
  private lazy val streamInIter = streamIn.iterator
  private val streamOut_ = new scala.collection.mutable.Queue[BigInt]
  val streamOut: Seq[BigInt] = streamOut_
  def done = !streamInIter.hasNext

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

  // unpack normal output data types
  def unpackOutputStream[T<:Data](gen: T, lanesOut: Int): Seq[Double] = {
    gen match {
      case s: SInt => 
        streamOut.map(x => (0 until lanesOut).map{ idx => {
          // TODO: doesn't work if width is > 32
          ((x >> (gen.getWidth * idx)) % pow(2, gen.getWidth).toInt).toDouble
        }}).flatten.toSeq
      case f: FixedPoint =>
        f.asInstanceOf[FixedPoint].binaryPoint match {
          case KnownBinaryPoint(binaryPoint) =>
            streamOut.map(x => (0 until lanesOut).map{ idx => {
              // TODO: doesn't work if width is > 32
              val y = (x >> (gen.getWidth * idx)) % pow(2, gen.getWidth).toInt
              toDoubleFromUnsigned(y, gen.getWidth, binaryPoint)
            }}).flatten.toSeq
          case _ =>
            throw DspException(s"Error: packInput: Can't create FixedPoint from signal template $f")
        }
      case r: DspReal =>
        streamOut.map(x => (0 until lanesOut).map{ idx => {
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
        streamOut.map(x => (0 until lanesOut).map{ idx => {
          // TODO: doesn't work if width is > 32
          val imag = (x >> ((gen.real.getWidth + gen.imaginary.getWidth) * idx)) % pow(2, gen.imaginary.getWidth).toInt
          val real = (x >> ((gen.real.getWidth + gen.imaginary.getWidth) * idx + gen.imaginary.getWidth)) % pow(2, gen.real.getWidth).toInt
          Complex(real.toDouble, imag.toDouble)
        }}).flatten.toSeq
      case "fixed" =>
        gen.real.asInstanceOf[FixedPoint].binaryPoint match {
          case KnownBinaryPoint(binaryPoint) =>
            streamOut.map(x => (0 until lanesOut).map{ idx => {
              val imag = (x >> ((gen.real.getWidth + gen.imaginary.getWidth) * idx)) % pow(2, gen.imaginary.getWidth).toInt
              val real = (x >> ((gen.real.getWidth + gen.imaginary.getWidth) * idx + gen.imaginary.getWidth)) % pow(2, gen.real.getWidth).toInt
              Complex(toDoubleFromUnsigned(real, gen.real.getWidth, binaryPoint), toDoubleFromUnsigned(imag, gen.imaginary.getWidth, binaryPoint))
            }}).flatten.toSeq
          case _ =>
            throw DspException(s"Error: packInput: Can't create FixedPoint from signal template ${gen.getClass.getName}")
        }
      case "real" =>
        streamOut.map(x => (0 until lanesOut).map{ idx => {
          // [stevo]: comes out as (imaginary, real) because it's alphabetical
          val imag = (x >> ((gen.real.getWidth + gen.imaginary.getWidth) * idx))
          val real = (x >> ((gen.real.getWidth + gen.imaginary.getWidth) * idx + gen.imaginary.getWidth))
          Complex(bigIntBitsToDouble(real), bigIntBitsToDouble(imag))
        }}).flatten.toSeq
      case _ => 
        throw DspException(s"Error: packInput: DspComplex has unknown underlying type ${gen.getClass.getName}")
    }
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
        assert(err < epsilon || r.real < epsilon, s"Error: mismatch in real value on output $index of ${err*100}%\n\tReference: ${r.real}\n\tChisel:    ${c.real}")
      }
      if (c.imag != r.imag) {
        val err = abs(c.imag-r.imag)/(abs(r.imag)+epsilon)
        assert(err < epsilon, s"Error: mismatch in imag value on output $index of ${err*100}%\n\tReference: ${r.imag}\n\tChisel:    ${c.imag}")
      }
    }
  }

  private val axi = dut.io.axi

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

  val axiDataWidth = dut.io.axi.w.bits.data.getWidth
  val axiDataBytes = axiDataWidth / 8
  val burstLen = axiDataBytes
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

  def axiWriteAs[T<:Data](addr: Int, value: Double, typ: T): Unit = {

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

  override def step(n: Int): Unit = {
    if (streamInValid && streamInIter.hasNext) {
      poke(dut.io.in.valid, 1)
      poke(dut.io.in.bits, streamInIter.next)
    } else {
      poke(dut.io.in.valid, 0)
    }
    if (peek(dut.io.out.valid) != BigInt(0)) {
      streamOut_ += peek(dut.io.out.bits)
    }
    super.step(1)
    if (n > 1) step(n - 1)
  }
}

