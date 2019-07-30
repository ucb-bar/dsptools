package freechips.rocketchip.amba.axi4stream

import AXI4StreamWidthAdapter._
import chisel3._
import chisel3.core.requireIsHardware
import chisel3.util.{Cat, log2Ceil}
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
) extends LazyModule()(Parameters.empty) {

  val node = AXI4StreamAdapterNode(masterFn, slaveFn)

  lazy val module = new LazyModuleImp(this) {
    val (in, inEdge) = node.in.head
    val (out, outEdge) = node.out.head

    val (adata, f0) = dataAdapter(in.bits.data, in.fire())
    val (alast, f1) = lastAdapter(in.bits.last, in.fire())
    val (astrb, f2) = strbAdapter(in.bits.strb, in.fire())
    val (akeep, f3) = keepAdapter(in.bits.keep, in.fire())
    val (auser, f4) = userAdapter(in.bits.user, in.fire())
    val (aid,   f5) = idAdapter(in.bits.id, in.fire())
    val (adest, f6) = destAdapter(in.bits.dest, in.fire())

    assert(f0 === f1)
    assert(f0 === f2)
    assert(f0 === f3)
    assert(f0 === f4)
    assert(f0 === f5)
    assert(f0 === f6)

    in.ready := out.ready
    out.valid := f0

    out.bits.data := adata
    out.bits.last := alast
    out.bits.strb := astrb
    out.bits.keep := akeep
    out.bits.user := auser
    out.bits.id   := aid
    out.bits.dest := adest
  }
}

object AXI4StreamWidthAdapter {
  type AdapterFun = (UInt, Bool) => (UInt, Bool)

  val identity: AdapterFun = (u, f) => (u, f)

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
           ): AXI4StreamAdapterNode = {
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

  def nToOneCatAdapter(n: Int): AdapterFun = (u, fire) => {
    require(n > 0)
    requireIsHardware(u)

    val regs = Seq.fill(n - 1) { Reg(chiselTypeOf(u)) }
    val cnt = RegInit(0.U(log2Ceil(n).W))
    when (fire) { cnt := cnt +& 1.U }

    for ((r, i) <- regs.zipWithIndex) {
      when (fire && cnt === i.U) {
        r := u
      }
    }

    val adaptedU = regs.foldLeft(u) { case (prev, r) => Cat(prev, r) }
    val adaptedF = cnt === (n - 1).U

    (adaptedU, adaptedF)
  }

  def nToOneOrAdapater(n: Int): AdapterFun = (u, fire) => {
    val cat = nToOneCatAdapter(n)(u, fire)
    (cat._1.orR(), cat._2)
  }

  def nToOne(n: Int): AXI4StreamAdapterNode =
    apply(
      masterFn = ms => {
        require(ms.masters.size == 1)
        val newN = ms.masters.head.n * n
        val newU = ms.masters.head.u * n
        val newNumMasters = ms.masters.head.numMasters
        AXI4StreamMasterPortParameters(
          Seq(AXI4StreamMasterParameters(s"${n}_to_one", n = newN, u = newU, numMasters = newNumMasters))
        )
      },
      slaveFn = ss => {
        require(ss.slaves.size == 1)
        ss
      },
      dataAdapter = nToOneCatAdapter(n),
      lastAdapter = nToOneOrAdapater(n),
      strbAdapter = nToOneCatAdapter(n),
      keepAdapter = nToOneCatAdapter(n),
      userAdapter = nToOneCatAdapter(n),
      idAdapter = identity,
      destAdapter = identity
    )
}