package freechips.rocketchip.amba.axi4stream

import AXI4StreamWidthAdapter._
import chisel3.UInt
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp}

class AXI4StreamWidthAdapter
(
  masterFn: AXI4StreamMasterPortParameters => AXI4StreamMasterPortParameters,
  slaveFn:  AXI4StreamSlavePortParameters  => AXI4StreamSlavePortParameters,
  dataAdapter: AdapterFun = identity,
  lastAdapter: AdapterFun = identity,
  strbAdapter: AdapterFun = identity,
  keepAdapter: AdapterFun = identity,
  userAdapter: AdapterFun = identity,
  idAdapter: AdapterFun   = identity,
  destAdapter: AdapterFun = identity
)(implicit p: Parameters) extends LazyModule {

  val node = AXI4StreamAdapterNode(masterFn, slaveFn)

  lazy val module = new LazyModuleImp(this) {
    val (in, inEdge) = node.in.head
    val (out, outEdge) = node.out.head

    in.ready := out.ready
    out.valid := in.valid

    out.bits.data := dataAdapter(in.bits.data)
    out.bits.last := lastAdapter(in.bits.last)
    out.bits.strb := strbAdapter(in.bits.strb)
    out.bits.keep := keepAdapter(in.bits.keep)
    out.bits.user := userAdapter(in.bits.user)
    out.bits.id   := idAdapter(in.bits.id)
    out.bits.dest := destAdapter(in.bits.dest)
  }
}

object AXI4StreamWidthAdapter {
  type AdapterFun = UInt => UInt

  val identity: AdapterFun = u => u

  def apply(
             masterFn: AXI4StreamMasterPortParameters => AXI4StreamMasterPortParameters,
             slaveFn:  AXI4StreamSlavePortParameters  => AXI4StreamSlavePortParameters,
             dataAdapter: AdapterFun = identity,
             lastAdapter: AdapterFun = identity,
             strbAdapter: AdapterFun = identity,
             keepAdapter: AdapterFun = identity,
             userAdapter: AdapterFun = identity,
             idAdapter: AdapterFun   = identity,
             destAdapter: AdapterFun = identity
           )(implicit p: Parameters): AXI4StreamAdapterNode = {
    val widthAdapter = LazyModule(new AXI4StreamWidthAdapter(
      masterFn    = masterFn,
      slaveFn     = slaveFn,
      dataAdapter = dataAdapter,
      lastAdapter = lastAdapter,
      strbAdapter = strbAdapter,
      keepAdapter = keepAdapter,
      userAdapter = userAdapter,
      idAdapter   = idAdapter,
      destAdapter = destAdapter
    ))
    widthAdapter.node
  }
}