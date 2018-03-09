package freechips.rocketchip.amba.axi4stream

import chisel3._
import chisel3.util.{Counter, log2Ceil}
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

class AXI4StreamFuzzer(
  transactions: Seq[AXI4StreamTransaction],
  n: Int = 8,
  u: Int = 0,
  numMasters: Int = 1,
  intraPacketGap: Int = 1,
  interPacketGap: Int = 5,
  noiseMaker: (Int, Bool) => UInt = {
    (wide: Int, increment: Bool) =>
      LFSRNoiseMaker(wide=wide, increment=increment)
  })(implicit p: Parameters) extends LazyModule {
  require(intraPacketGap > 0)
  require(interPacketGap > 0)

  val masterParams = AXI4StreamMasterParameters(
    name = "AXI4 Stream Fuzzer",
    n = n,
    u = u,
    numMasters = numMasters
  )

  val nTransactions = transactions.length

  val node = AXI4StreamMasterNode(masterParams)

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle {
      val finished = Output(Bool())
    })

    val (out, edge) = node.out.head

    val (data, last, user) =
      transactions.map(t => (t.data.U, t.last.B, t.user.U)).unzip3
    val (id, dest) =
      transactions.map(t => (t.id.U, t.dest.U)).unzip
    // -1 has special meaning for strb and keep
    // it means fill with all 1s (they are both n bits)
    def fillAllOnesIfMinusOne(in: BigInt): BigInt = if (in == -1) {
      BigInt("1" * n, 2)
    } else {
      in
    }
    val (strb, keep) =
      transactions.map(t => (fillAllOnesIfMinusOne(t.strb).U, fillAllOnesIfMinusOne(t.keep).U)).unzip

    val dataVec = VecInit(data)
    val lastVec = VecInit(last)
    val strbVec = VecInit(strb)
    val keepVec = VecInit(keep)
    val userVec = VecInit(user)
    val idVec   = VecInit(id)
    val destVec = VecInit(dest)

    val interPacketGo = out.fire() && out.bits.last
    val intraPacketGo = out.fire() && !out.bits.last

    val interPacketCounter = RegInit(UInt(log2Ceil(interPacketGap).W), 1.U)
    val intraPacketCounter = RegInit(UInt(log2Ceil(intraPacketGap).W), 1.U)

    val interPacketDone: Bool = interPacketCounter === 0.U
    val intraPacketDone: Bool = intraPacketCounter === 0.U

    val interPacketNoise = noiseMaker(interPacketGap, interPacketGo)
    val intraPacketNoise = noiseMaker(intraPacketGap, intraPacketGo)

    when (interPacketGo) {
      interPacketCounter := interPacketNoise
    }
    when (intraPacketGo) {
      intraPacketCounter := intraPacketNoise
    }
    when (!interPacketDone) {
      interPacketCounter := interPacketCounter - 1.U
    }
    when (!intraPacketDone) {
      intraPacketCounter := intraPacketCounter - 1.U
    }

    out.valid := interPacketDone && intraPacketDone

    val (transactionCounter, transactionCounterDone) = Counter(out.fire(), nTransactions)

    val finished = RegInit(false.B)
    finished := finished || transactionCounterDone
    io.finished := finished

    out.bits.data := dataVec(transactionCounter)
    out.bits.last := lastVec(transactionCounter)
    out.bits.strb := strbVec(transactionCounter)
    out.bits.keep := keepVec(transactionCounter)
    out.bits.user := userVec(transactionCounter)
    out.bits.id   := idVec  (transactionCounter)
    out.bits.dest := destVec(transactionCounter)

  }
}

object AXI4StreamFuzzer {
  def apply(
    transactions: Seq[AXI4StreamTransaction],
    n: Int = 8,
    u: Int = 0,
    numMasters: Int = 1,
    intraPacketGap: Int = 1,
    interPacketGap: Int = 5,
    noiseMaker: (Int, Bool) => UInt = {
     (wide: Int, increment: Bool) =>
       LFSRNoiseMaker(wide=wide, increment=increment)
    })(implicit p: Parameters): AXI4StreamMasterNode = {

    val fuzzer = LazyModule(
      new AXI4StreamFuzzer(transactions = transactions,
        n = n, u = u, numMasters = numMasters,
        intraPacketGap = intraPacketGap, interPacketGap = interPacketGap,
        noiseMaker = noiseMaker))
    fuzzer.node
  }
  def bundleParams(transactions: Seq[AXI4StreamTransaction],
            params: AXI4StreamBundleParameters = AXI4StreamBundleParameters(n = 8),
            intraPacketGap: Int = 1,
            interPacketGap: Int = 5,
            noiseMaker: (Int, Bool) => UInt = {
              (wide: Int, increment: Bool) =>
                LFSRNoiseMaker(wide=wide, increment=increment)
            })(implicit p: Parameters): AXI4StreamMasterNode = {
      AXI4StreamFuzzer(
        transactions,
        n = params.n,
        u = params.u,
        numMasters = 1 << params.i,
        intraPacketGap = intraPacketGap,
        interPacketGap = interPacketGap,
        noiseMaker = noiseMaker
      )
    }
  }