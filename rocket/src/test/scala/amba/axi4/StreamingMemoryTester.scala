package freechips.rocketchip.amba.axi4

import chisel3.iotesters.PeekPokeTester
import dspblocks.AXI4StandaloneBlock
import freechips.rocketchip.amba.axi4stream.{AXI4StreamModel, AXI4StreamTransaction, AXI4StreamTransactionExpect}
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy.{AddressSet, LazyModule}
import freechips.rocketchip.system.BaseConfig
import org.scalatest.{FlatSpec, Matchers}

class StreamingMemoryTester(dut: StreamingMemory with AXI4StandaloneBlock, silentFail: Boolean = false)
  extends PeekPokeTester(dut.module)
  with AXI4StreamModel {

  val mod = dut.module
  val master = bindMaster(dut.in)
  val slave = bindSlave(dut.out)

  poke(mod.io.enable, true)
  poke(mod.io.watchdogInterval, 0x0FFFFFFF)

  val beatBytes = dut.in.params.n

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

class StreamingMemorySpec extends FlatSpec with Matchers {
  implicit val p: Parameters = (new BaseConfig).toInstance

  "StreamingMemory" should "stream in and out of SRAM" in {
    val lazyDut = LazyModule(new StreamingMemory(
      address = AddressSet(0x0, 0xFFFF),
      beatBytes = 8,
    ) with AXI4StandaloneBlock {
      lazy val mem = None
    })
    chisel3.iotesters.Driver.execute(Array("-fiwv", "-tbn", "treadle", "-tiwv", "-tivsuv"), () => lazyDut.module) {
      c => new StreamingMemoryTester(lazyDut)
    } should be (true)
  }
}