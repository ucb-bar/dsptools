// See LICENSE for license details

package freechips.rocketchip.tilelink

import chisel3._
import chisel3.internal.firrtl.Width
import dspblocks._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util.HeterogeneousBag

import scala.collection.mutable

/*
class TLInStreamOutFIFO(val csrBase: BigInt, val csrSize: BigInt, val memAddress: AddressSet, val beatBytes: Int = 4, name: Option[String] = None, errors: Seq[AddressSet] = Seq(), val outerTLfunc: Option[() => TLBlindInputNode] = None, val outerAXI4Sfunc: Option[() => AXI4StreamBlindOutputNode] = None)(implicit p: Parameters) extends LazyModule with TLHasCSR with TLDspBlock
{

  val resources: Seq[Resource] =
    name.map(new SimpleDevice(_, Seq("berkeley,fifo0")).reg("mem")).getOrElse(new MemoryDevice().reg)

  require(mem.isDefined, "Requires a memory interface")

  // We require the address range to include an entire beat (for the write mask)
  require ((memAddress.mask & (beatBytes-1)) == beatBytes-1)

  addControl("start")
  addControl("end")
  addControl("en")
  addControl("repeat")

  val streamNode: MixedNode[AXI4StreamMasterPortParameters, AXI4StreamSlavePortParameters, AXI4StreamEdgeParameters, AXI4StreamBundle, AXI4StreamMasterPortParameters, AXI4StreamSlavePortParameters, AXI4StreamEdgeParameters, AXI4StreamBundle] = outerAXI4Sfunc.map(_()).getOrElse(new AXI4StreamOutputNode)

  val outerTL: MixedNode[TLClientPortParameters, TLManagerPortParameters, TLEdgeIn, TLBundle, TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLBundle] = outerTLfunc.map({ tl =>
    val node = tl()
    mem.get := node
    node
  }).getOrElse(mem.get)

  val internal = LazyModule(new TLInStreamOutFIFOInternal(memAddress, beatBytes, new CSRRecord(csrMap)))

  streamNode := internal.streamNode

  lazy val module = new TLInStreamOutFIFOModule(this)
}

class TLInStreamOutFIFOModule(outer: TLInStreamOutFIFO) extends LazyModuleImp(outer) {
  val io = IO(new Bundle {
    val mem = outer.outerTL.bundleIn
    val stream = outer.streamNode.bundleOut
  })

}

class TLInStreamOutFIFOInternal(memAddress: AddressSet, beatBytes: Int, csrs: CSRRecord)(implicit p: Parameters) extends LazyModule {
  val streamNode = AXI4StreamMasterNode(Seq(AXI4StreamMasterPortParameters(
    Seq(AXI4StreamMasterParameters(
      "fifoOut",
      n = beatBytes)
    ))))

  lazy val module = new LazyModuleImp(this){
    val io: Bundle {
      val streamOut: HeterogeneousBag[AXI4StreamBundle]
    } = IO(new Bundle {
      val streamOut: HeterogeneousBag[AXI4StreamBundle] = streamNode.bundleOut
    })
  }
}

package tester {

  import chisel3.iotesters.PeekPokeTester

  class MMAPFIFOTester(c: TLInStreamOutFIFOModule) extends PeekPokeTester(c) with TLMasterModel[TLInStreamOutFIFOModule] {
    val memTL = c.io.mem(0)

    tlWriteWord(0, 3)
    tlWriteWord(4, 2)

    println(s"Read out ${tlReadWord(0)} at addr 0")
  }

}

object JustForNow2 {
  def main(args: Array[String]): Unit = {
    import freechips.rocketchip.coreplex._
    implicit val p: Parameters = Parameters.root((new BaseCoreplexConfig).toInstance)
    val outerAXI4S = () => AXI4StreamBlindOutputNode(Seq(AXI4StreamSlavePortParameters(
    )))
    val outerTL = () => TLBlindInputNode(Seq(TLClientPortParameters(Seq(TLClientParameters("tlfifo")))))
    val dut = () => LazyModule(new TLInStreamOutFIFO(0, 512, AddressSet(0x200, 0xff), 8, outerAXI4Sfunc=Some(outerAXI4S), outerTLfunc=Some(outerTL))(p)).module
    chisel3.Driver.execute(Array("-X", "verilog"), dut)
    println(chisel3.Driver.emit(dut))

    chisel3.iotesters.Driver.execute(Array("--backend-name", "firrtl", "-fiwv"), dut) { c=> new tester.MMAPFIFOTester(c) }
  }
}
*/