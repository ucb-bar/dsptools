package ofdm

import chisel3._
import chisel3.experimental.FixedPoint
import chisel3.core.requireIsChiselType
import chisel3.util._
import dspblocks._
import dsptools.numbers._
import dsptools.numbers.implicits._
import freechips.rocketchip.amba.axi4.{AXI4BlindInputNode, AXI4MasterParameters, AXI4MasterPortParameters}
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.coreplex.BaseCoreplexConfig
import freechips.rocketchip.diplomacy._

import scala.collection.Seq

case class AutocorrParams[T <: Data]
(
  genIn: T,
  maxApart: Int,
  maxOverlap: Int,
  address: AddressSet,
  genOut: Option[T] = None,
  name: String = "autocorr",
  base: Int = 0,
  beatBytes: Int = 4,
  addPipeDelay: Int = 3,
  mulPipeDelay: Int = 3
) {
  requireIsChiselType(genIn,  s"genIn ($genIn) must be chisel type")
  genOut.foreach(g => requireIsChiselType(g, s"genOut ($g) must be chisel type"))
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
  val io = IO(new Bundle {
    val in = outer.streamIn.bundleOut
    val out = outer.streamOut.bundleIn
    val mem = outer.mem.bundleOut
  })

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

  lazy val module = new AutocorrModule(this)
}

class AutocorrModule[T <: Data : Ring](outer: Autocorr[T]) extends LazyModuleImp(outer) {
  val streamNode  = outer.streamNode
  val memNode     = outer.mem.get

  // get fields from outer class
  val genIn      : T   = outer.autocorrParams.genIn
  val genOut     : T   = outer.autocorrParams.genOut.getOrElse(genIn)
  val shrMaxDepth: Int = outer.autocorrParams.maxApart
  val maxOverlap : Int = outer.autocorrParams.maxOverlap

  val io = IO(new Bundle {
    val in  = streamNode.bundleIn
    val out = streamNode.bundleOut
    val mem = memNode.bundleIn
  })

  val csrs = outer.csrs.module.io.csrs

  // cast input to T
  val io_in         = io.in(0)
  val io_in_data: T = io_in.bits.data.asTypeOf(genIn)
  val io_out        = io.out(0)

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
      streamIn  = () => AXI4StreamBlindInputNode(Seq(AXI4StreamMasterPortParameters(Seq(AXI4StreamMasterParameters(
        "autocorr",
        bundleParams = AXI4StreamBundleParameters(n = inWidthBytes)
      ))))),
      streamOut = () => AXI4StreamBlindOutputNode(Seq(AXI4StreamSlavePortParameters(Seq(AXI4StreamSlaveParameters(
        bundleParams = AXI4StreamBundleParameters(n = outWidthBytes)
      ))))),
      mem       = () => AXI4BlindInputNode(Seq(AXI4MasterPortParameters(Seq(
        AXI4MasterParameters(
          "autocorr"))))

      ))
    chisel3.Driver.execute(Array("-X", "verilog"), () => LazyModule(new AutocorrBlind(params, blindParams)).module)
  }
}