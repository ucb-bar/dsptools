package autocorr


import chisel3._
import chisel3.core.requireIsChiselType
import chisel3.util._
import dspblocks._
import dsptools.numbers.implicits._
import dsptools.numbers.Ring
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._

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
  val io_in:  IrrevocableIO[AXI4StreamBundlePayload]         = io.in(0)
  val io_in_data: T = io_in.bits.data.asTypeOf(genIn)
  val io_out: IrrevocableIO[AXI4StreamBundlePayload]        = io.out(0)

  // add delayed path to correlate with
  val shr = Module(new AutocorrShiftRegister(genIn, shrMaxDepth))

  shr.io.depth.bits  := csrs(CSRDepthApart)
  shr.io.depth.valid := csrs(CSRSetDepthApart) =/= 0.U
  shr.io.in.valid    := io_in.fire()
  shr.io.in.bits     := io_in_data

  val shr_out_valid              = RegNext(shr.io.in.valid)
  val shr_in_delay               = RegNext(io_in_data)

  // correlate short and long path
  val prod: T = shr_in_delay * shr.io.out.bits

  // sliding window
  val sum = Module(new OverlapSum(genOut, maxOverlap, pipeDelay = outer.autocorrParams.addPipeDelay))

  sum.io.depth.bits := csrs(CSRDepthOverlap)
  sum.io.depth.valid := csrs(CSRSetDepthOverlap)

  // pipeline the multiply here
  sum.io.in.bits := ShiftRegister(prod, outer.autocorrParams.mulPipeDelay, en = io_in.fire())
  sum.io.in.valid := ShiftRegister(shr_out_valid, outer.autocorrParams.mulPipeDelay, en = io_in.fire())

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
  inFlight := inFlight + io_in.fire() - io_out.fire()
  assert(!(inFlight === 0.U) || !io_out.fire(),
    s"When there are 0 in-flight transactions, there should be no output")

  io_in.ready := inFlight < queueDepth.U

}

class AutocorrShiftRegister[T <: Data](val gen: T, val maxDepth: Int) extends Module {
  require(maxDepth > 1, s"Depth must be > 1, got $maxDepth")

  val io = IO(new Bundle {
    val depth = Input(Valid(UInt(log2Ceil(maxDepth + 1).W)))
    val in    = Input(Valid(gen.cloneType))
    val out   = Output(Valid(gen.cloneType))
  })

  val mem        = SyncReadMem(maxDepth, gen)
  val readIdx    = Wire(UInt(log2Ceil(maxDepth).W))
  val readIdxReg = RegInit(0.U(log2Ceil(maxDepth).W) - (maxDepth - 1).U)
  val writeIdx   = RegInit(0.U(log2Ceil(maxDepth).W))

  when (io.depth.valid) {
    val diff = writeIdx - io.depth.bits
    when (diff >= 0.U) {
      readIdx := diff
    }   .otherwise {
      readIdx := maxDepth.U - diff
    }
  }   .otherwise {
    readIdx := readIdxReg
  }

  when (io.in.valid) {
    readIdxReg := Mux(readIdx < (maxDepth - 1).U, readIdx + 1.U, 0.U)
    writeIdx := Mux(writeIdx < (maxDepth - 1).U, writeIdx + 1.U, 0.U)
  }   .otherwise {
    readIdxReg := readIdx
  }

  mem.write(writeIdx, io.in.bits)
  io.out.bits := mem.read(readIdx)
  io.out.valid := RegNext(io.in.fire(), init = false.B)
}

class OverlapSum[T <: Data : Ring](val gen: T, val maxDepth: Int, val pipeDelay: Int = 1) extends Module {
  require(maxDepth > 0, s"Depth must be > 0, got $maxDepth")

  val io = IO(new Bundle {
    val depth = Input(Valid(UInt(log2Ceil(maxDepth + 1).W)))
    val in    = Input(Valid(gen.cloneType))
    val out   = Output(Valid(gen.cloneType))
  })

  val depth = RegInit(maxDepth.U)
  when (io.depth.valid) {
    depth := io.depth.bits
  }

  val shr                                                = Reg(Vec(maxDepth - 1, gen.cloneType))
  val shrSelected: IndexedSeq[T] = shr.zipWithIndex.map { case (reg, idx) =>
      val included: Bool = (idx + 1).U < depth
      Mux(included, reg, 0.U.asTypeOf(reg)) //Ring[T].zero) //0.U.asTypeOf(reg))
  }
  val sum: T = (Seq(io.in.bits) ++ shrSelected).reduce(_ + _)
  io.out.bits := ShiftRegister(sum, pipeDelay)
  io.out.valid := ShiftRegister(io.in.fire(), pipeDelay, false.B, true.B)

  shr.scanLeft(io.in.bits) { case (in, out) =>
      when (io.in.fire()) {
        out := in
      }
      out
  }
}