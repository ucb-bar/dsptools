package freechips.rocketchip.amba.axi4stream

import AXI4StreamWidthAdapter._
import breeze.linalg.max
import chisel3._
import chisel3.internal.requireIsHardware
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
  destAdapter: AdapterFun = identity,
  nameSuffix: String = ""
) extends LazyModule()(Parameters.empty) {

  val node = AXI4StreamAdapterNode(masterFn, slaveFn)

  lazy val module = new LazyModuleImp(this) {
    override def desiredName: String = "AXI4StreamWidthAdapater" + (if (nameSuffix.length > 0) {
      s"_$nameSuffix"
    } else { "" })
    val (in, inEdge) = node.in.head
    val (out, outEdge) = node.out.head

    val (adata, ov0, ir0) = dataAdapter(in.bits.data, in.valid, out.ready)
    val (alast, ov1, ir1) = lastAdapter(in.bits.last, in.valid, out.ready)
    val (astrb, ov2, ir2) = strbAdapter(in.bits.strb, in.valid, out.ready)
    val (akeep, ov3, ir3) = keepAdapter(in.bits.keep, in.valid, out.ready)
    val (auser, ov4, ir4) = userAdapter(in.bits.user, in.valid, out.ready)
    val (aid,   ov5, ir5) = idAdapter(in.bits.id, in.valid, out.ready)
    val (adest, ov6, ir6) = destAdapter(in.bits.dest, in.valid, out.ready)

    assert(ov0 === ov1)
    assert(ov0 === ov2)
    assert(ov0 === ov3)
    assert(ov0 === ov4)
    assert(ir0 === ir1)
    assert(ir0 === ir2)
    assert(ir0 === ir3)
    assert(ir0 === ir4)

    in.ready := ir0
    out.valid := ov0

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
  //                    d,   iv, or    =>     d,   ov, ir
  type AdapterFun = (UInt, Bool, Bool) => (UInt, Bool, Bool)

  val identity: AdapterFun = (u, iv, or) => (u, iv, or)

  def apply(
             masterFn: AXI4StreamMasterPortParameters => AXI4StreamMasterPortParameters,
             slaveFn:  AXI4StreamSlavePortParameters  => AXI4StreamSlavePortParameters,
             dataAdapter: AdapterFun = identity,
             lastAdapter: AdapterFun = identity,
             strbAdapter: AdapterFun = identity,
             keepAdapter: AdapterFun = identity,
             userAdapter: AdapterFun = identity,
             idAdapter: AdapterFun   = identity,
             destAdapter: AdapterFun = identity,
             nameSuffix: String = "",
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
      destAdapter = destAdapter,
      nameSuffix = nameSuffix
    ))
    widthAdapter.node
  }

  def nToOneCatAdapter(n: Int): AdapterFun = (u, iv, or) => {
    require(n > 0)
    requireIsHardware(u)

    val regs = Seq.fill(n - 1) { Reg(chiselTypeOf(u)) }
    val cnt = RegInit(0.U(log2Ceil(n).W))
    when (iv && or) { cnt := Mux(cnt === (n-1).U, 0.U, cnt +& 1.U) }

    for ((r, i) <- regs.zipWithIndex) {
      when (iv && or && cnt === i.U) {
        r := u
      }
    }

    val od = regs.foldRight(u) { case (prev, r) => Cat(r, prev) }
    val ov = cnt === (n - 1).U && iv

    (od, ov, or)
  }

  def nToOneOrAdapater(n: Int): AdapterFun = (u, iv, or) => {
    val cat = nToOneCatAdapter(n)(u, iv, or)
    (cat._1.orR(), cat._2, cat._3)
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
      destAdapter = identity,
      nameSuffix = s"${n}_to_1"
    )

  def oneToNSliceAdapter(n: Int): AdapterFun = (d, iv, or) => {
    require(((d.getWidth + n - 1) / n) * n == d.getWidth, s"width ${d.getWidth} must be divisible by $n")
    val l = d.getWidth / n
    val slices = VecInit(for (i <- n-1 to 0 by -1) yield {
      if (d.getWidth == 0) {
        d
      } else {
        d(d.getWidth - 1 - i * l, max(0, d.getWidth - (i + 1) * l))
      }
    })

    val cnt = RegInit(0.U(log2Ceil(n).W))
    when (iv && or) { cnt := Mux(cnt === (n-1).U, 0.U, cnt +& 1.U) }

    val dOut = slices(cnt)

    (dOut, iv, cnt === (n - 1).U && or)
  }

  def oneToNLastAdapter(n: Int): AdapterFun = (d, iv, or) => {
    val cnt = RegInit(0.U(log2Ceil(n).W))
    when (iv && or) { cnt := Mux(cnt === (n-1).U, 0.U, cnt +& 1.U) }

    (d =/= 0.U && cnt === (n - 1).U, iv, cnt === (n - 1).U && or)
  }

  def oneToN(n: Int): AXI4StreamAdapterNode =
    apply(
      masterFn = ms => {
        require(ms.masters.size == 1)
        val newN = (ms.masters.head.n + n - 1) / n
        val newU = (ms.masters.head.u + n - 1) / n
        val newNumMasters = ms.masters.head.numMasters
        AXI4StreamMasterPortParameters(
          Seq(AXI4StreamMasterParameters(s"one_to_$n", n = newN, u = newU, numMasters = newNumMasters))
        )
      },
      slaveFn = ss => {
        require(ss.slaves.size == 1)
        ss
      },
      dataAdapter = oneToNSliceAdapter(n),
      lastAdapter = oneToNLastAdapter(n),
      strbAdapter = oneToNSliceAdapter(n),
      keepAdapter = oneToNSliceAdapter(n),
      userAdapter = oneToNSliceAdapter(n),
      idAdapter = identity,
      destAdapter = identity,
      nameSuffix = s"1_to_$n"
    )

}