package freechips.rocketchip.amba.axi4stream

import chisel3._
import chisel3.util.log2Ceil
import dspblocks.{DspBlock, HasCSR}
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.{RegField, RegFieldDesc}
import freechips.rocketchip.tilelink._

abstract class StreamMux[D, U, EO, EI, B <: Data](val beatBytes: Int) extends LazyModule()(Parameters.empty)
  with DspBlock[D, U, EO, EI, B] with HasCSR {
  val streamNode = AXI4StreamNexusNode(
    masterFn = (ms: Seq[AXI4StreamMasterPortParameters]) =>
      AXI4StreamMasterPortParameters(ms.map(_.masters).reduce(_ ++ _)),
    slaveFn = ss => {
      AXI4StreamSlavePortParameters(ss.map(_.slaves).reduce(_ ++ _))
    }
  )

  lazy val module = new LazyModuleImp(this) {
    val (ins, _) = streamNode.in.unzip
    val (outs, _) = streamNode.out.unzip

    val selBits = log2Ceil(ins.length + 1)
    // priority is given to the earlier entry, i.e. if sels(0) and sels(1) point to the same thing then out(0) will be
    // connected to the selected input and out(1) will be blocked
    // if sels(i) = ins.length, then it is disconnected from inputs (valid -> false)
    // all sels are initialized to be disconnected
    println(s"outs.length = ${outs.length}")
    val sels = RegInit(VecInit(Seq.fill(outs.length)(ins.length.U(selBits.W))))

    for (in <- ins) {
      in.ready := false.B // unless overridden by a selection below, input should not accept transactions
    }
    for ((out, outIdx) <- outs.zipWithIndex) {
      val sel = sels(outIdx)
      // check if earlier entries have reserved sel
      val selTaken = sels.take(outIdx).map(_ === sel).foldLeft(false.B)(_ || _)
      val selCorrected = Mux(selTaken, ins.length.U, sel)
      out.valid := false.B
      out.bits := DontCare
      for ((in, inIdx) <- ins.zipWithIndex) {
        when (selCorrected === inIdx.U) {
          out.bits := in.bits
          out.valid := in.valid
          in.ready := out.ready
        }
      }
    }

    regmap(
      (for ((s, sIdx) <- sels.zipWithIndex) yield {
        sIdx * beatBytes -> Seq(RegField(selBits, s,
          RegFieldDesc(s"streamSel$sIdx", "select for stream mux")
        ))
      }): _*
    )
  }
}

class AXI4StreamMux(val address: AddressSet, beatBytes: Int)
extends StreamMux[AXI4MasterPortParameters, AXI4SlavePortParameters, AXI4EdgeParameters, AXI4EdgeParameters, AXI4Bundle](beatBytes = beatBytes)
{
  val registerNode = AXI4RegisterNode(address, beatBytes = beatBytes)
  val mem = Some(registerNode)

  override def regmap(mapping: (Int, Seq[RegField])*): Unit = registerNode.regmap(mapping: _*)
}

class TLStreamMux(val address: AddressSet, beatBytes: Int)
extends StreamMux[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle](beatBytes = beatBytes) {
  val device = new SimpleDevice("streamMux", devcompat = Seq("bwrc,streamMux"))
  val registerNode = TLRegisterNode(Seq(address), device = device, beatBytes = beatBytes)
  val mem = Some(registerNode)

  override def regmap(mapping: (Int, Seq[RegField])*): Unit = registerNode.regmap(mapping: _*)
}

object StreamMux {
  def axi(address: AddressSet, beatBytes: Int = 4)(implicit valName: ValName): (AXI4StreamNexusNode, AXI4RegisterNode) =
  {
    val mux = LazyModule(new AXI4StreamMux(address = address, beatBytes = beatBytes))
    (mux.streamNode, mux.registerNode)
  }
  def tl(address: AddressSet, beatBytes: Int = 4)(implicit valName: ValName): (AXI4StreamNexusNode, TLRegisterNode) = {
    val mux = LazyModule(new TLStreamMux(address = address, beatBytes = beatBytes))
    (mux.streamNode, mux.registerNode)
  }
}
