// See LICENSE for license details.

package dspblocks

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.system.BaseConfig
import org.scalatest.{FlatSpec, Matchers}

class DspBlockSpec extends FlatSpec with Matchers {
  implicit val p: Parameters = (new BaseConfig).toInstance

  behavior of "Passthrough"

  it should "work with AXI4" in {
    val params = PassthroughParams(depth = 5)
    val lazymod = LazyModule(new AXI4Passthrough(params) with AXI4StandaloneBlock)
    val dut = () => lazymod.module

    chisel3.iotesters.Driver.execute(Array("-tbn", "firrtl", "-fiwv"), dut) {
      c => new AXI4PassthroughTester(lazymod)
    } should be (true)
  }

  it should "work with APB" in {
    val params = PassthroughParams(depth = 5)
    val lazymod = LazyModule(new APBPassthrough(params) with APBStandaloneBlock)
    val dut = () => lazymod.module

    chisel3.iotesters.Driver.execute(Array("-tbn", "firrtl", "-fiwv"), dut) {
      c => new APBPassthroughTester(lazymod)
    } should be (true)
  }

  it should "work with TL" ignore {
    val params = PassthroughParams(depth = 5)
    val lazymod = LazyModule(new TLPassthrough(params) with TLStandaloneBlock)
    val dut = () => lazymod.module

    chisel3.iotesters.Driver.execute(Array("-tbn", "verilator", "-fiwv"), dut) {
      c => new TLPassthroughTester(lazymod)
    } should be (true)
  }

  behavior of "Byte Rotate"

  it should "work with AXI4" in {
    val lazymod = LazyModule(new AXI4ByteRotate() with AXI4StandaloneBlock)
    val dut = () => lazymod.module

    chisel3.iotesters.Driver.execute(Array(/*"-tiv",*/ "-tbn", "firrtl", "-fiwv"), dut) {
      c => new AXI4ByteRotateTester(lazymod)
    } should be (true)
  }

  it should "work with APB" in {
    val lazymod = LazyModule(new APBByteRotate() with APBStandaloneBlock)
    val dut = () => lazymod.module

    chisel3.iotesters.Driver.execute(Array("-tbn", "firrtl", "-fiwv"), dut) {
      c => new APBByteRotateTester(lazymod)
    } should be (true)

  }

  it should "work with TL" ignore {
    val lazymod = LazyModule(new TLByteRotate() with TLStandaloneBlock)
    val dut = () => lazymod.module

    chisel3.iotesters.Driver.execute(Array("-tbn", "verilator", "-fiwv"), dut) {
      c => new TLByteRotateTester(lazymod)
    } should be (true)
  }

  behavior of "PTBR Chain"

  it should "work with APB" in {
    val lazymod = LazyModule(new APBChain(Seq(
      implicit p => LazyModule(new APBPassthrough(PassthroughParams(5))),
      implicit p => LazyModule(new APBByteRotate() {
        override def csrAddress: AddressSet = AddressSet(0x100, 0xFF)
      })
    )) with APBStandaloneBlock)
    val dut = () => lazymod.module

    chisel3.iotesters.Driver.execute(Array("-tbn", "firrtl", "-fiwv"), dut) {
      c => new APBPTBRTester(lazymod, 0, 0x100)
    } should be (true)

  }

  it should "work with AXI4" in {
    val lazymod = LazyModule(new AXI4Chain(Seq(
      implicit p => LazyModule(new AXI4Passthrough(PassthroughParams(5))),
      implicit p => LazyModule(new AXI4ByteRotate() {
        override def csrAddress: AddressSet = AddressSet(0x100, 0xFF)
      })
    )) with AXI4StandaloneBlock)
    val dut = () => lazymod.module

    chisel3.iotesters.Driver.execute(Array("-tbn", "firrtl", "-fiwv"), dut) {
      c => new AXI4PTBRTester(lazymod, 0, 0x100)
    } should be (true)
  }

  it should "work with TL" ignore {
    val lazymod = LazyModule(new TLChain(Seq(
      implicit p => LazyModule(new TLPassthrough(PassthroughParams(5))),
      implicit p => LazyModule(new TLByteRotate() {
        override def csrAddress: AddressSet = AddressSet(0x100, 0xFF)
      })
    )) with TLStandaloneBlock)
    val dut = () => lazymod.module

    chisel3.iotesters.Driver.execute(Array("-tbn", "verilator", "-fiwv"), dut) {
      c => new TLPTBRTester(lazymod, 0, 0x100)
    } should be (true)
  }
}
