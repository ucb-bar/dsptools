package ofdm

import chisel3._
import chisel3.experimental.FixedPoint
import chisel3.core.requireIsChiselType
import chisel3.util._
import dspblocks._
import dsptools.numbers._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.coreplex.BaseCoreplexConfig
import freechips.rocketchip.diplomacy._

import scala.collection.Seq

case class AutocorrParams[T <: Data]
(
  protoIn: T,
  maxApart: Int,
  maxOverlap: Int,
  address: AddressSet,
  protoOut: Option[T] = None,
  name: String = "autocorr",
  base: Int = 0,
  beatBytes: Int = 4,
  addPipeDelay: Int = 3,
  mulPipeDelay: Int = 3
) {
  requireIsChiselType(protoIn,  s"genIn ($protoIn) must be chisel type")
  protoOut.foreach(g => requireIsChiselType(g, s"genOut ($g) must be chisel type"))
}

/* Depth of shift register used for autocorr */
case object CSRDepthApart extends CSRField {
  val name = "depthApart"
}

/* Set value of CSRDepthApart */
case object CSRSetDepthApart extends CSRField {
  val name = "setDepthApart"
}

/* Overlap between two things being correlated */
case object CSRDepthOverlap extends CSRField {
  val name = "depthOverlap"
}

/* Set value of CSRDepthOverlap */
case object CSRSetDepthOverlap extends CSRField {
  val name = "setDepthOverlap"
}

object AutocorrBlind {
  def apply[T <: Data : Ring]
  (
    autocorrParams: AutocorrParams[T],
    blindNodes: DspBlock.AXI4BlindNodes
  )(implicit p: Parameters) = {
    DspBlock.blindWrapper(
      () => new Autocorr(autocorrParams),
      blindNodes
    ) //.asInstanceOf[LazyModule with AXI4DspBlock]
  }
}

class AutocorrBlind[T <: Data : Ring](val autocorrParams: AutocorrParams[T],
                                      blindNodes: DspBlock.AXI4BlindNodes)(
                                     implicit p: Parameters) extends LazyModule {

  val streamIn  = blindNodes.streamIn()
  val streamOut = blindNodes.streamOut()
  val mem       = blindNodes.mem()

  val autocorr = LazyModule(new Autocorr(autocorrParams))
  autocorr.streamNode := streamIn
  streamOut := autocorr.streamNode
  autocorr.mem.get := mem

  lazy val module = new AutocorrBlindModule(this)
}

class AutocorrBlindModule[T <: Data : Ring](val outer: AutocorrBlind[T]) extends LazyModuleImp(outer) {
  val (in, _) = outer.streamIn.in.unzip
  val (out, _) = outer.streamOut.out.unzip
  val (mem, _) = outer.mem.out.unzip

}

class Autocorr[T <: Data : Ring](val autocorrParams: AutocorrParams[T])
                                (implicit p: Parameters) extends LazyModule
  with AXI4DspBlock with AXI4HasCSR {

  addControl(CSRDepthApart)
  addControl(CSRSetDepthApart)
  addControl(CSRDepthOverlap)
  addControl(CSRSetDepthOverlap)


  val streamNode     = AXI4StreamIdentityNode() //streamNodeOpt.getOrElse(streamInOpt.get)

  def beatBytes : Int        = autocorrParams.beatBytes
  def csrAddress: AddressSet = autocorrParams.address
  def csrSize   : Int        = 8 * csrMap.size
  def csrBase   : Int        = autocorrParams.base

  makeCSRs()

  // csrs.node := mem.get

  lazy val module = Module(new AutocorrModule(this))
}

class AutocorrModule[T <: Data : Ring](outer: Autocorr[T]) extends LazyModuleImp(outer) {
  val streamNode  = outer.streamNode
  val memNode     = outer.mem.get

  // get fields from outer class
  val genIn      : T   = outer.autocorrParams.protoIn
  val genOut     : T   = outer.autocorrParams.protoOut.getOrElse(genIn)
  val shrMaxDepth: Int = outer.autocorrParams.maxApart
  val maxOverlap : Int = outer.autocorrParams.maxOverlap


  val (in, _) = streamNode.in.unzip
  val (out, _) = streamNode.out.unzip
  val (mem, _) = memNode.in.unzip

  val csrs = outer.csrs.module.io.csrs

  // cast input to T
  val io_in         = in(0)
  val io_in_data: T = io_in.bits.data.asTypeOf(genIn)
  val io_out        = out(0)

  // add delayed path to correlate with
  val shr = Module(new ShiftRegisterMem(genIn, shrMaxDepth))

  shr.io.depth.bits  := csrs(CSRDepthApart)
  shr.io.depth.valid := csrs(CSRSetDepthApart) =/= 0.U
  shr.io.in.valid    := io_in.fire()
  shr.io.in.bits     := io_in_data

  val shr_out_valid              = RegNext(shr.io.in.valid)
  val shr_in_delay               = RegNext(io_in_data)

  // correlate short and long path
  val toMult: T = shr.io.out.bits match {
    case m: DspComplex[_] => m.conj().asInstanceOf[T]
    case b => b
  }
  val prod: T = shr_in_delay * toMult /*match {
    case m: DspComplex[_] => {
      val w = Wire(m.cloneType)
      w.real := m.abssq()
      w.imag := 0.U.asTypeOf(m.imag)
      w.asInstanceOf[T]
    }
  }*/

  // sliding window
  val sum = Module(new OverlapSum(genOut, maxOverlap, pipeDelay = outer.autocorrParams.addPipeDelay))

  sum.io.depth.bits := csrs(CSRDepthOverlap)
  sum.io.depth.valid := csrs(CSRSetDepthOverlap)

  // pipeline the multiply here
  sum.io.in.bits := ShiftRegister(prod, outer.autocorrParams.mulPipeDelay, en = io_in.fire())
  sum.io.in.valid := ShiftRegister(shr_out_valid, outer.autocorrParams.mulPipeDelay, resetData = false.B, en = io_in.fire())

  val sum_out_packed = sum.io.out.bits.asUInt
  val sum_out_irrevocable = Wire(Irrevocable(sum_out_packed.cloneType))
  sum_out_irrevocable.bits := sum_out_packed
  sum_out_irrevocable.valid := sum.io.out.valid

  val queueDepth = // shrMaxDepth + maxOverlap +
    outer.autocorrParams.addPipeDelay + outer.autocorrParams.mulPipeDelay

  val outputQueue = Queue(sum_out_irrevocable, entries = queueDepth)
  io_out.valid := outputQueue.valid
  io_out.bits.data := outputQueue.bits
  outputQueue.ready := io_out.ready

  // keep track of in-flight transactions
  val inFlight = RegInit(0.U(log2Ceil(queueDepth + 1).W))
  inFlight := inFlight + sum_out_irrevocable.fire() - io_out.fire()
  assert(!(inFlight === 0.U) || !io_out.fire(),
    s"When there are 0 in-flight transactions, there should be no output")

  io_in.ready := inFlight < queueDepth.U
}

class AutocorrConfigIO[T <: Data](params: AutocorrParams[T]) extends Bundle {
  val depthApart = Input(UInt(log2Ceil(params.maxApart+1).W))
  val depthOverlap = Input(UInt(log2Ceil(params.maxOverlap+1).W))
}

class AutocorrSimpleIO[T <: Data](params: AutocorrParams[T]) extends Bundle {
  val in = Flipped(Valid(params.protoIn))
  val out = Valid(params.protoOut.getOrElse(params.protoIn))

  val config = new AutocorrConfigIO(params)
}

class AutocorrSimple[T <: Data : Ring](params: AutocorrParams[T]) extends Module {
  // get fields from outer class
  val genIn      : T   = params.protoIn
  val genOut     : T   = params.protoOut.getOrElse(genIn)
  val shrMaxDepth: Int = params.maxApart
  val maxOverlap : Int = params.maxOverlap

  val io = IO(new AutocorrSimpleIO(params))

  // add delayed path to correlate with
  val shr = Module(new ShiftRegisterMem(genIn, shrMaxDepth))

  shr.io.depth.bits  := io.config.depthApart
  shr.io.depth.valid := io.config.depthApart =/= RegNext(io.config.depthApart)
  shr.io.in.valid    := io.in.fire()
  shr.io.in.bits     := io.in.bits

  val shr_out_valid              = RegNext(shr.io.in.valid)
  val shr_in_delay               = RegNext(io.in.bits)

  // correlate short and long path
  val toMult: T = shr.io.out.bits match {
    case m: DspComplex[_] => m.conj().asInstanceOf[T]
    case b => b
  }
  val prod: T = shr_in_delay * toMult /*match {
    case m: DspComplex[_] => {
      val w = Wire(m.cloneType)
      w.real := m.abssq()
      w.imag := 0.U.asTypeOf(m.imag)
      w.asInstanceOf[T]
    }
  }*/

  // sliding window
  val sum = Module(new OverlapSum(genOut, maxOverlap, pipeDelay = params.addPipeDelay))

  sum.io.depth.bits := io.config.depthOverlap
  sum.io.depth.valid := io.config.depthOverlap =/= RegNext(io.config.depthOverlap)

  // pipeline the multiply here
  sum.io.in.bits := ShiftRegister(prod, params.mulPipeDelay, en = io.in.fire())
  sum.io.in.valid := ShiftRegister(shr_out_valid, params.mulPipeDelay, resetData = false.B, en = io.in.fire())

  val sum_out_packed = sum.io.out.bits.asUInt
  val sum_out_irrevocable = Wire(Irrevocable(sum_out_packed.cloneType))
  sum_out_irrevocable.bits := sum_out_packed
  sum_out_irrevocable.valid := sum.io.out.valid

  io.out.valid := sum.io.out.valid
  io.out.bits := sum.io.out.bits

}

object BuildSampleAutocorr {
  def main(args: Array[String]): Unit = {
    implicit val p: Parameters = Parameters.root((new BaseCoreplexConfig).toInstance)
    val params = AutocorrParams(
      DspComplex(FixedPoint(16.W, 14.BP), FixedPoint(16.W, 14.BP)),
      //DspComplex(FixedPoint(8.W, 4.BP), FixedPoint(8.W, 4.BP)),
      // genOut=Some(DspComplex(FixedPoint(16.W, 8.BP), FixedPoint(16.W, 8.BP))),
      maxApart = 32,
      maxOverlap = 32,
      address = AddressSet(0x0, 0xffffffffL),
      beatBytes = 8)
    val inWidthBytes = 4 //(params.genIn.getWidth + 7) / 8
    val outWidthBytes = 4 //params.genOut.map(x => (x.getWidth + 7)/8).getOrElse(inWidthBytes)

    println(s"In bytes = $inWidthBytes and out bytes = $outWidthBytes")

    val blindParams = DspBlockBlindNodes(
      streamIn  = () => AXI4StreamIdentityNode(),
      streamOut = () => AXI4StreamIdentityNode(),
      mem       = () => AXI4IdentityNode())
    chisel3.Driver.execute(Array("-X", "verilog"), () => LazyModule(new AutocorrBlind(params, blindParams)).module)
  }
}