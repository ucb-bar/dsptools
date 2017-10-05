package dspblocks

import amba.axi4.AXI4MasterModel
import chisel3._
import chisel3.iotesters.PeekPokeTester
import dspblocks.BlindWrapperModule._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.tilelink.TLMasterModel

abstract class PassthroughTester[D, U, EO, EI, B <: Data, T <: Passthrough[D, U, EO, EI, B]](c: BlindWrapperModule[D, U, EO, EI, B, T]) extends PeekPokeTester(c) {
  def resetMem(): Unit
  def readAddr(addr: BigInt): BigInt

  val out = c.out(0)
  val in  = c.in(0)

  resetMem()
  poke(in.valid, 0)
  poke(out.ready, 0)
  step(5)

  val depth = readAddr(BigInt(0)).toInt
  val expectedDepth = c.outer.internal.params.depth
  println(s"Depth was $depth, should be $expectedDepth")
  require(depth == expectedDepth, s"Depth was $depth, should be $expectedDepth")

  var currentIn   = 0
  var expectedOut = 0

  poke(out.ready, 0)

  // fill queue
  poke(in.valid, 1)
  for (i <- 0 until expectedDepth) {
    poke(in.bits.data, currentIn)
    expect(in.ready, 1)
    step(1)
    currentIn += 1
  }
  // queue should be full
  expect(in.ready, 0)
  expect(out.valid, 1)

  poke(in.valid, 0)
  poke(out.ready, 1)

  // drain queue
  while (expectedOut < expectedDepth) {
    if (peek(out.valid) != BigInt(0)) {
      expect(out.bits.data, expectedOut)
      expectedOut += 1
    }
    step(1)
  }
}

class AXI4PassthroughTester(c: AXI4BlindWrapperModule[AXI4Passthrough])
  extends PassthroughTester(c)
    with AXI4MasterModel[AXI4BlindWrapperModule[AXI4Passthrough]] {
  def memAXI = c.mem(0)

  def resetMem(): Unit = {
    axiReset()
  }

  def readAddr(addr: BigInt): BigInt = {
    axiReadWord(addr)
  }
}

class TLPassthroughTester(c: TLBlindWrapperModule[TLPassthrough])
  extends PassthroughTester(c)
    with TLMasterModel[TLBlindWrapperModule[TLPassthrough]] {

  def memTL = c.mem(0)

  def resetMem(): Unit = {
    tlReset()
  }

  def readAddr(addr: BigInt): BigInt = {
    tlReadWord(addr)
  }

}