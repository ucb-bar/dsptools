package dspblocks

import amba.axi4stream.AXI4StreamNodeHandle
import breeze.stats.distributions.Uniform
import chisel3.{Bundle, Module}
import chisel3.iotesters.PeekPokeTester
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy.{AddressSet, LazyModule, LazyModuleImp, TransferSizes}
import freechips.rocketchip.tilelink._
import org.scalatest.{FlatSpec, Matchers}
import DoubleToBigIntRand._
import amba.axi4.AXI4MasterModel
import chisel3.core.Flipped
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.interrupts._

class TestModule(
                  val inP: AXI4StreamBundleParameters,
                  val outP: AXI4StreamSlaveParameters,
                  val len: Int,
                  val transactions: Seq[AXI4StreamTransaction] =
                    AXI4StreamTransaction.defaultSeq(100).map(_.randData(Uniform(0.0, 65535.0)))
                ) extends Module {
  implicit val p: Parameters = Parameters.empty

  val lazyMod = LazyModule(new LazyModule() {
    override def moduleName: String = "OhHaiMark"
    val fuzzer = AXI4StreamFuzzer.bundleParams(transactions, inP)
    val reg    = LazyModule(new AXI4DspRegister(len))
    val outNode = AXI4StreamSlaveNode(outP)

    val memMaster = AXI4MasterNode(Seq(AXI4MasterPortParameters(Seq(AXI4MasterParameters(
      "testModule"
    )))))

      /*TLClientNode(Seq(TLClientPortParameters(
      Seq(TLClientParameters(
        "testModule",
        supportsProbe = TransferSizes(1),
        supportsGet = TransferSizes(1),
        supportsPutFull = TransferSizes(1)
      ))
    )))*/

    reg.streamNode := fuzzer
    outNode        := reg.streamNode
    // memMaster      := reg.mem.get
    reg.mem.get    := memMaster

    lazy val module = new LazyModuleImp(this) {
      val (sinkBundle, edge) = outNode.in.head
      val (memBundle, memEdge) = memMaster.out.head

      val out = IO(AXI4StreamBundle(sinkBundle.params))
      val mem = IO(Flipped(AXI4Bundle(memBundle.params)))

      out <> sinkBundle
      memBundle <> mem
    }
  })

  val mod = Module(lazyMod.module)

  val io = IO(new Bundle {
    val out = new AXI4StreamBundle(mod.edge.bundle)
    val mem = Flipped(new AXI4Bundle(mod.memEdge.bundle))
  })

  io.out <> mod.out
  mod.mem <> io.mem
}

class TestModuleTester(c: TestModule,
                       expectTranslator: Seq[AXI4StreamTransaction] => Seq[AXI4StreamTransactionExpect] =
                       { _.map(t => AXI4StreamTransactionExpect(data = Some(t.data))) }
                      )
  extends PeekPokeTester(c) with AXI4StreamSlaveModel[TestModule] with AXI4MasterModel[TestModule] {

  override val memAXI: AXI4Bundle = c.io.mem
  axiReset()
  reset(10)

  bindSlave(c.io.out).addExpects(
    expectTranslator(c.transactions)
  )

  println(s"${axiReadWord(0)} is the veclen")
}

class DspRegisterSpec extends FlatSpec with Matchers {
  behavior of "AXI4DspRegister"

  it should "be able to read and write" in {
    val inP  = AXI4StreamBundleParameters(n = 2)
    val outP = AXI4StreamSlaveParameters()
    val transactions = AXI4StreamTransaction.defaultSeq(64).zipWithIndex.map({case (t, i) => t.copy(data = i) })

    chisel3.iotesters.Driver(() => new TestModule(inP, outP, 64, transactions) /*, backendType = "verilator"*/) {
      c => new TestModuleTester(c) {
        axiWriteWord(0, 64)
        axiWriteWord(0x10, 15)
        axiWriteWord(0x8, 0xFF00)
        step(64)
        axiWriteWord(0x8, 0x00FF)
        stepToCompletion()
      }
    } should be (true)
  }
}
