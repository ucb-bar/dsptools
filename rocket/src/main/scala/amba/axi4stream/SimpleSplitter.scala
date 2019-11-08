package freechips.rocketchip.amba.axi4stream

import chisel3._
import freechips.rocketchip.amba.axi4stream.{AXI4StreamMasterPortParameters, AXI4StreamNexusNode, AXI4StreamSlavePortParameters}
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._

class SimpleSplitter() extends LazyModule()(Parameters.empty) {

  val node = AXI4StreamNexusNode(
    masterFn = { seq => seq.reduce({ (a: AXI4StreamMasterPortParameters, b: AXI4StreamMasterPortParameters) => AXI4StreamMasterPortParameters(a.masterParams.union(b.masterParams))}) },
    slaveFn  = { seq => seq.reduce({ (_: AXI4StreamSlavePortParameters, b: AXI4StreamSlavePortParameters)  => AXI4StreamSlavePortParameters (b.slaveParams .union(b.slaveParams)) }) }
  )

  lazy val module = new LazyModuleImp(this) {
    require(node.in.length == 1, "Only one input to splitter allowed")
    val (in, _) = node.in.head
    in.ready := true.B
    node.out.foreach { case (out, edge) =>
        require(edge.slave.slaveParams.alwaysReady)

        out.valid := in.valid
        out.bits  := in.bits
        assert(!reset.toBool || in.ready)
    }
  }
}

object SimpleSplitter {
  def apply()(implicit p: Parameters): AXI4StreamNexusNode = {
    val splitter = LazyModule(new SimpleSplitter())
    splitter.node
  }
}