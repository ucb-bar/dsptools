package freechips.rocketchip.amba.axi4

import chisel3.iotesters.PeekPokeTester
import dspblocks.{AXI4DspBlock, AXI4StandaloneBlock, TLDspBlock, TLStandaloneBlock}
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy.{AddressSet, LazyModule, LazyModuleImp, LazyModuleImpLike, MixedNode, NodeHandle}
import freechips.rocketchip.subsystem.{PeripheryBus, PeripheryBusParams}
import freechips.rocketchip.system.BaseConfig
import freechips.rocketchip.tilelink.{TLBundleParameters, TLFragmenter, TLIdentityNode, TLToAXI4}
import org.scalatest.{FlatSpec, Matchers}

class StreamingAXI4DMAWithMemoryTester(dut: StreamingAXI4DMAWithMemory with AXI4StandaloneBlock, silentFail: Boolean = false)
  extends PeekPokeTester(dut.module)
  with AXI4StreamModel {

  val mod = dut.module
  val master = bindMaster(dut.in)
  val slave = bindSlave(dut.out)

  poke(mod.io.enable, true)
  poke(mod.io.watchdogInterval, 0x0FFFFFFF)

  val beatBytes = dut.in.params.n

  master.addTransactions((0 until 50).map(i => AXI4StreamTransaction(data = i)))
  master.addTransactions((0 until 50).map(i => AXI4StreamTransaction(data = i)))

  poke(mod.io.streamToMemRequest.bits.baseAddress, beatBytes)
  poke(mod.io.streamToMemRequest.bits.length, 50 - 1)
  poke(mod.io.streamToMemRequest.bits.fixedAddress, false)
  poke(mod.io.streamToMemRequest.bits.cycles, 0)


  poke(mod.io.enable, true)
  poke(mod.io.watchdogInterval, 0x0FFFFFFF)

  poke(mod.io.streamToMemRequest.valid, true)
  poke(mod.io.memToStreamRequest.valid, false)

  var cycle = 0
  while (peek(dut.module.io.streamToMemRequest.ready) == BigInt(0) && cycle < 100) {
    cycle += 1
    step(1)
  }
  step(1)
  poke(dut.module.io.streamToMemRequest.valid, false)

  cycle = 0
  while (peek(dut.module.io.writeComplete) != BigInt(1) && cycle < 100) {
    cycle += 1
    step(1)
  }

  slave.addExpects((0 until 50 - 4).map(i => AXI4StreamTransactionExpect(data = Some(i + 3))))

  poke(mod.io.memToStreamRequest.bits.baseAddress, beatBytes * 4)
  poke(mod.io.memToStreamRequest.bits.length, 50 - 3 - 1)
  poke(mod.io.memToStreamRequest.bits.fixedAddress, false)
  poke(mod.io.memToStreamRequest.bits.cycles, 0)

  poke(mod.io.memToStreamRequest.valid, true)
  cycle = 0
  while (peek(mod.io.memToStreamRequest.ready) == BigInt(0) && cycle <= 100) {
    cycle += 1
    step(1)
  }
  step(1)
  poke(mod.io.memToStreamRequest.valid, false)

  stepToCompletion(silentFail = silentFail)
}

class StreamingAXI4DMAWithWithCSRWithScratchpadTester
(
  dut: StreamingAXI4DMAWithCSRWithScratchpad with AXI4StandaloneBlock,
  silentFail: Boolean = false
) extends PeekPokeTester(dut.module) with AXI4StreamModel with AXI4MasterModel {
  override def memAXI: AXI4Bundle = dut.ioMem.get
  val mod = dut.module
  val master = bindMaster(dut.in)
  val slave = bindSlave(dut.out)

  val csrBase = dut.csrAddress.base
  val memBase = dut.scratchpadAddress.base
  val beatBytes = dut.in.params.n

  memWriteWord(csrBase + beatBytes * 0, 1) // enable
  memWriteWord(csrBase + beatBytes * 2, 0x0FFFFFFF) // watchdog interval

  master.addTransactions((0 until 50).map(i => AXI4StreamTransaction(data = i)))

  memWriteWord(csrBase + beatBytes * 4, beatBytes) // base address
  memWriteWord(csrBase + beatBytes * 5, 50 - 1) // length
  memWriteWord(csrBase + beatBytes * 6, 0) // cycles
  memWriteWord(csrBase + beatBytes * 7, 0) // fixed address
  memWriteWord(csrBase + beatBytes * 8, 0) // initiate s->m

  var cycle = 0
  while (memReadWord(csrBase + beatBytes * 8) != BigInt(0) && cycle < 800) {
    cycle += 1
    step(1)
  }
  step(1)

  master.addTransactions((0 until 600).map(i => AXI4StreamTransaction(data = i)))

  memWriteWord(csrBase + beatBytes * 4, beatBytes * (50 + 1)) // base address
  memWriteWord(csrBase + beatBytes * 5, 600 - 1) // length
  memWriteWord(csrBase + beatBytes * 8, 0) // initiate s->m

  cycle = 0
  while (memReadWord(csrBase + beatBytes * 8) != BigInt(0) && cycle < 800) {
    cycle += 1
    step(1)
  }
  step(1)

  val repeats = 2
  for (_ <- 0 to repeats) {
    slave.addExpects((0 until 50 - 3).map(i => AXI4StreamTransactionExpect(data = Some(i + 3))))
  }

  memWriteWord(csrBase + beatBytes * 9, beatBytes * 4) // base address
  memWriteWord(csrBase + beatBytes * 10, 50 - 3 - 1) // length
  memWriteWord(csrBase + beatBytes * 11, repeats) // cycles
  memWriteWord(csrBase + beatBytes * 12, 0) // fixed address
  memWriteWord(csrBase + beatBytes * 13, 0) // initiate m->s

  cycle = 0
  while (memReadWord(csrBase + beatBytes * 13) != BigInt(0) && cycle <= 400) {
    cycle += 1
    step(1)
  }
  step(1)

  stepToCompletion(silentFail = silentFail)
}

class DMASimplifierTester(dut: DMASimplifier) extends PeekPokeTester(dut) {
  import dut.{addrWidth, beatBytes, complexLenWidth, maxSimpleLength, simpleLenWidth}

  poke(dut.io.in.valid, true)
  poke(dut.io.out.ready, false)

  poke(dut.io.in.bits.fixedAddress, 0)
  poke(dut.io.in.bits.cycles, 0)
  poke(dut.io.in.bits.baseAddress, beatBytes)
  poke(dut.io.in.bits.length, maxSimpleLength + 1)

  expect(dut.io.out.valid, 1)
  step(5)

  poke(dut.io.out.ready, 1)
  expect(dut.io.out.bits.baseAddress, beatBytes)
  expect(dut.io.out.bits.fixedAddress, 0)
  expect(dut.io.out.bits.length, maxSimpleLength)
  expect(dut.io.in.ready, 0)

  step(1)
  expect(dut.io.out.bits.baseAddress, beatBytes + beatBytes * (maxSimpleLength + 1))
  expect(dut.io.out.bits.fixedAddress, 0)
  expect(dut.io.out.bits.length, 1)
  expect(dut.io.in.ready, 1)

  step(1)
  poke(dut.io.in.bits.cycles, 2)

  for (c <- 0 to 2) {
    expect(dut.io.out.bits.baseAddress, beatBytes)
    expect(dut.io.out.bits.fixedAddress, 0)
    expect(dut.io.out.bits.length, maxSimpleLength)
    expect(dut.io.in.ready, 0)
    step(1)

    expect(dut.io.out.bits.baseAddress, beatBytes + beatBytes * (maxSimpleLength + 1))
    expect(dut.io.out.bits.fixedAddress, 0)
    expect(dut.io.out.bits.length, 1)
    expect(dut.io.in.ready, c == 2)
    step(1)
  }
}

class DmaSpec extends FlatSpec with Matchers {
  implicit val p: Parameters = (new BaseConfig).toInstance

  behavior of "DMASimplifier"

  it should "simplify complex DMA requests" in {
    val dut = () => new DMASimplifier(8, 8, 2, 8)
    chisel3.iotesters.Driver.execute(Array("-tbn", "treadle"), dut) { c => new DMASimplifierTester(c) } should be (true)
  }

  behavior of "DMA"

  it should "stream in and out of SRAM" in {
    val lazyDut = LazyModule(new StreamingAXI4DMAWithMemory(
      address = AddressSet(0x0, 0xFFFF),
      beatBytes = 8,
    ) with AXI4StandaloneBlock {
      lazy val mem = None
    })
    chisel3.iotesters.Driver.execute(Array("-fiwv", "-tbn", "verilator", "-tiwv", "-tivsuv"), () => lazyDut.module) {
      c => new StreamingAXI4DMAWithMemoryTester(lazyDut)
    } should be (true)
  }

  it should "work with CSRs and scratchpad" in {
    val lazyDut = LazyModule(new StreamingAXI4DMAWithCSRWithScratchpad(
      csrAddress = AddressSet(0x20000, 0xFF),
      scratchpadAddress = AddressSet(0x0, 0x1FFFF),
      beatBytes = 8,
    ) with AXI4StandaloneBlock)
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv"), () => lazyDut.module) {
      c => new StreamingAXI4DMAWithWithCSRWithScratchpadTester(lazyDut, true)
    } should be (true)
  }

  it should "elaborate connected to the pbus" in {
    class WithPBUS extends TLDspBlock {
      val dma = LazyModule(new StreamingAXI4DMAWithCSRWithScratchpad(
        csrAddress = AddressSet(0x400, 0xFF),
        scratchpadAddress = AddressSet(0x0, 0x3FF),
        beatBytes = 8,
      ))
      val pbus = LazyModule(new PeripheryBus(PeripheryBusParams(
        beatBytes = 16,
        blockBytes = 16 * 8,
        atomics = None,
      ), "periphery_bus"))
      pbus.toFixedWidthSlave(Some("dma")) {
        dma.mem.get := AXI4Buffer() := TLToAXI4() := TLFragmenter(8, 8 *8, holdFirstDeny = true)
      }
      /**
        * Diplomatic node for AXI4-Stream interfaces
        */
      override val streamNode = dma.streamNode
      /**
        * Diplmatic node for memory interface
        * Some blocks might not need memory mapping, so this is an Option[]
        */
      val mem = Some(TLIdentityNode())
      pbus.inwardNode := mem.get

      override def module = new LazyModuleImp(this) {

      }
    }
    val lazyDut = LazyModule(new WithPBUS with TLStandaloneBlock {
      // override def standaloneParams: TLBundleParameters = super.standaloneParams.copy(dataBits = 128)
    })
    chisel3.Driver.execute(Array[String](), () => lazyDut.module)
  }
}