package freechips.rocketchip.amba.axi4stream

import chisel3._
import chisel3.util.log2Ceil
import dspblocks.HasCSR
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.{RegField, RegFieldDesc}
import freechips.rocketchip.tilelink._

abstract class StreamMux[D, U, EO, EI, B <: Data]() extends LazyModule()(Parameters.empty) with HasCSR {
  val streamNode = AXI4StreamNexusNode(
    masterFn = ms => {
      ms.head // TODO
    },
    slaveFn = ss => {
      require(ss.length == 1)
      ss.head
    }
  )

  lazy val module = new LazyModuleImp(this) {
    val (ins, _) = streamNode.in.unzip
    val (out, _) = streamNode.out.head

    val selBits = log2Ceil(ins.length)
    val sel = RegInit(0.U(selBits.W))

    out.bits := ins.head.bits
    out.valid := ins.head.valid
    ins.head.ready := out.ready

    // drop the first input, it's the default
    for ((in, idx) <- ins.tail.zipWithIndex) {
      when (sel === idx.U) {
        out.bits := in.bits
        out.valid := in.valid
        in.ready := out.ready
      }
    }

    regmap(
      0x0 -> Seq(RegField(selBits, sel,
        RegFieldDesc("streamSel", "select for mux with n input streams, 1 output stream")
      ))
    )
  }
}

class AXI4StreamMux(address: AddressSet, beatBytes: Int)
extends StreamMux[AXI4MasterPortParameters, AXI4SlavePortParameters, AXI4EdgeParameters, AXI4EdgeParameters, AXI4Bundle]
{
  val registerNode = AXI4RegisterNode(address, beatBytes = beatBytes)

  override def regmap(mapping: (Int, Seq[RegField])*): Unit = registerNode.regmap(mapping: _*)
}

class TLStreamMux(address: AddressSet, beatBytes: Int)
extends StreamMux[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle] {
  val device = new SimpleDevice("streamMux", devcompat = Seq("bwrc,streamMux"))
  val registerNode = TLRegisterNode(Seq(address), device = device, beatBytes = beatBytes)

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
