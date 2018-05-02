package freechips.rocketchip.amba.axi4stream

import amba.axi4stream._
import breeze.stats.distributions.Uniform
import chisel3._
import freechips.rocketchip.diplomacy._
import org.scalatest.{FlatSpec, Matchers}
import chisel3.iotesters.PeekPokeTester
import freechips.rocketchip.config.Parameters
import DoubleToBigIntRand._

class TestModule(val inP: AXI4StreamBundleParameters,
                 outP: AXI4StreamSlaveParameters,
                 func: (AXI4StreamMasterNode, Parameters) => AXI4StreamNodeHandle) extends Module {
  implicit val p: Parameters = Parameters.empty
  val transactions = AXI4StreamTransaction.defaultSeq(100).map(_.randData(Uniform(0.0, 65535.0)))

  val lazyMod = LazyModule(new LazyModule() {
    val fuzzer = AXI4StreamFuzzer.bundleParams(transactions, inP)
    val outNode = AXI4StreamSlaveNode(outP)
    outNode := func(fuzzer, p)

    lazy val module = new LazyModuleImp(this) {
      val (sinkBundle, edge) = outNode.in.head

      val out = IO(AXI4StreamBundle(sinkBundle.params))

      out <> sinkBundle
    }
  })

  val mod = Module(lazyMod.module)

  val io = IO(new Bundle {
    val out = new AXI4StreamBundle(mod.edge.bundle)
  })

  io.out <> mod.out
}

class TestModuleTester(c: TestModule,
                       expectTranslator: Seq[AXI4StreamTransaction] => Seq[AXI4StreamTransactionExpect] =
                         { _.map(t => AXI4StreamTransactionExpect(data = Some(t.data))) }
                      )
  extends PeekPokeTester(c) with AXI4StreamSlaveModel[TestModule] {
  bindSlave(c.io.out).addExpects(
    expectTranslator(c.transactions)
  )
  stepToCompletion()
}

class AXI4StreamSpec extends FlatSpec with Matchers {
  behavior of "AXI4 Stream Nodes"
  it should "work with fuzzer and identity" in {
    val inP  = AXI4StreamBundleParameters(n = 2)
    val outP = AXI4StreamSlaveParameters()
    def func(in: AXI4StreamMasterNode, p: Parameters) = {
      implicit val pp = p
      val identity = AXI4StreamIdentityNode()
      identity := in

    }

    chisel3.iotesters.Driver.execute(Array("-fiwv"), () => new TestModule(inP, outP, func)) {
      c => new TestModuleTester(c)
    } should be(true)
  }

  it should "work with adapter that shrinks data" in {
    val inP  = AXI4StreamBundleParameters(n = 2)
    val outP = AXI4StreamSlaveParameters()
    def func(in: AXI4StreamMasterNode, p: Parameters) = {
      implicit val pp = p
      val adapter = AXI4StreamWidthAdapter(
        p => AXI4StreamAdapterNode.widthAdapter(p, _ => 1),
        s => s
      )
      adapter := in
    }

    chisel3.iotesters.Driver.execute(Array("-fiwv"), () => new TestModule(inP, outP, func)) {
      c => new TestModuleTester(c, expectTranslator = _.map(t => AXI4StreamTransactionExpect(data = Some(t.data & 0xFF))))
    } should be(true)
  }
}
