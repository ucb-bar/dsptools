package freechips.rocketchip.amba.axi4stream

import amba.axi4stream._
import breeze.stats.distributions.Uniform
import chisel3._
import chisel3.iotesters.PeekPokeTester
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import org.scalatest.{FlatSpec, Matchers}

class TestModule(val inP: AXI4StreamBundleParameters,
                 outP: AXI4StreamSlaveParameters,
                 func: (AXI4StreamMasterNode, Parameters) => AXI4StreamNodeHandle) extends Module {
  implicit val p: Parameters = Parameters.empty
  import DoubleToBigIntRand._
  val transactions = AXI4StreamTransaction.defaultSeq(100).map(_.randData(Uniform(0, 65535)))

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
  extends PeekPokeTester(c) with AXI4StreamSlaveModel {
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

    chisel3.iotesters.Driver.execute(Array("-tiwv"), () => new TestModule(inP, outP, func)) {
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

    chisel3.iotesters.Driver.execute(Array("-tiwv"), () => new TestModule(inP, outP, func)) {
      c => new TestModuleTester(c, expectTranslator = _.map(t => AXI4StreamTransactionExpect(data = Some(t.data & 0xFF))))
    } should be(true)
  }

  it should "work with one-to-two adapter" in {
    val inP  = AXI4StreamBundleParameters(n = 2)
    val outP = AXI4StreamSlaveParameters()
    def func(in: AXI4StreamMasterNode, x: Parameters) = {
      implicit val p = Parameters.empty
      val adapter = AXI4StreamWidthAdapter.oneToN(2)
      adapter := in
    }
    def expectTranslator(ts: Seq[AXI4StreamTransaction]): Seq[AXI4StreamTransactionExpect] = {
      ts.flatMap({ case t => Seq((t.data >> 0) & 0xFF, (t.data >> 8) & 0xFF) })
        .map(t => AXI4StreamTransactionExpect(data = Some(t)))
    }
    chisel3.iotesters.Driver.execute(Array("-tiwv"), () => new TestModule(inP, outP, func)) {
      c => new TestModuleTester(c, expectTranslator = expectTranslator)
    } should be(true)
  }

  it should "work with two-to-one adapter" in {
    val inP  = AXI4StreamBundleParameters(n = 2)
    val outP = AXI4StreamSlaveParameters()
    def func(in: AXI4StreamMasterNode, x: Parameters) = {
      implicit val p = Parameters.empty
      val adapter = AXI4StreamWidthAdapter.nToOne(2)
      adapter := in
    }
    def expectTranslator(ts: Seq[AXI4StreamTransaction]): Seq[AXI4StreamTransactionExpect] = {
      ts.map(_.data & 0xFFFF).grouped(2).map({ case l :: r :: Nil =>
        AXI4StreamTransactionExpect(data = Some((r << 16) | l))
      }).toSeq
    }
    chisel3.iotesters.Driver.execute(Array("-tiwv"), () => new TestModule(inP, outP, func)) {
      c => new TestModuleTester(c, expectTranslator = expectTranslator)
    } should be(true)
  }

  for (i <- 2 until 10) {
    it should s"work with one-to-$i to $i-to-one adapters back to back" in {
      val inP  = AXI4StreamBundleParameters(n = 2)
      val outP = AXI4StreamSlaveParameters()
      def func(in: AXI4StreamMasterNode, x: Parameters) = {
        implicit val p = Parameters.empty
        AXI4StreamWidthAdapter.oneToN(i) := AXI4StreamBuffer() := AXI4StreamWidthAdapter.nToOne(i) := in
      }
      def expectTranslator(ts: Seq[AXI4StreamTransaction]): Seq[AXI4StreamTransactionExpect] = {
        ts.map(t => AXI4StreamTransactionExpect(data = Some(t.data & 0xFFFF)))
      }
      chisel3.iotesters.Driver.execute(Array("-tiwv"), () => new TestModule(inP, outP, func)) {
        c => new TestModuleTester(c, expectTranslator = expectTranslator)
      } should be(true)

    }
  }
}
