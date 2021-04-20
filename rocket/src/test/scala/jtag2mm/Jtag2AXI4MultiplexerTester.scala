// SPDX-License-Identifier: Apache-2.0

package freechips.rocketchip.jtag2mm

import chisel3._
import chisel3.util._
import chisel3.experimental._
import chisel3.experimental.{withClockAndReset}

import dsptools._
import dsptools.numbers._

import dspblocks._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._

import chisel3.iotesters.Driver
import chisel3.iotesters.PeekPokeTester
import org.scalatest.{FlatSpec, Matchers}

//class Jtag2AXI4MultiplexerTester(dut: Jtag2AXI4Multiplexer) extends PeekPokeTester(dut.module) {
class Jtag2AXI4MultiplexerTester(dut: Jtag2AXI4Multiplexer) extends DspTester(dut.module) {
  def jtagReset(stepSize: Int = 1) {
    var i = 0
    while (i < 5) {
      poke(dut.ioJTAG.jtag.TCK, 0)
      poke(dut.ioJTAG.jtag.TMS, 1)
      step(stepSize)
      poke(dut.ioJTAG.jtag.TCK, 1)
      step(stepSize)
      i += 1
    }
  }

  def jtagSend(
    data:                BigInt,
    dataLength:          Int,
    dataNotInstruction: Boolean = true,
    stateResetNotIdle: Boolean = true,
    stepSize:            Int = 1
  ) {

    if (stateResetNotIdle) {
      poke(dut.ioJTAG.jtag.TCK, 0)
      poke(dut.ioJTAG.jtag.TMS, 0)
      step(stepSize)
      poke(dut.ioJTAG.jtag.TCK, 1)
      step(stepSize)
    }

    poke(dut.ioJTAG.jtag.TCK, 0)
    poke(dut.ioJTAG.jtag.TMS, 1)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 1)
    step(stepSize)

    if (!dataNotInstruction) {
      poke(dut.ioJTAG.jtag.TCK, 0)
      poke(dut.ioJTAG.jtag.TMS, 1)
      step(stepSize)
      poke(dut.ioJTAG.jtag.TCK, 1)
      step(stepSize)
    }

    poke(dut.ioJTAG.jtag.TCK, 0)
    poke(dut.ioJTAG.jtag.TMS, 0)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 1)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 0)
    poke(dut.ioJTAG.jtag.TMS, 0)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 1)
    step(stepSize)

    var i = 0
    while (i < dataLength - 1) {
      poke(dut.ioJTAG.jtag.TCK, 0)
      poke(dut.ioJTAG.jtag.TMS, 0)
      poke(dut.ioJTAG.jtag.TDI, data.testBit(i))
      step(stepSize)
      poke(dut.ioJTAG.jtag.TCK, 1)
      step(stepSize)
      i += 1
    }

    poke(dut.ioJTAG.jtag.TCK, 0)
    poke(dut.ioJTAG.jtag.TMS, 1) // 0
    poke(dut.ioJTAG.jtag.TDI, data.testBit(i))
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 1)
    step(stepSize)

    poke(dut.ioJTAG.jtag.TCK, 0)
    poke(dut.ioJTAG.jtag.TMS, 1)
    poke(dut.ioJTAG.jtag.TDI, 0)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 1)
    step(stepSize)

    poke(dut.ioJTAG.jtag.TCK, 0)
    poke(dut.ioJTAG.jtag.TMS, 0)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 1)
    step(stepSize)
  }

  val stepSize = 5

  step(5)
  poke(dut.outStream.ready, 1)
  step(1)
  peek(dut.outStream.bits.data)
  expect(dut.outStream.bits.data, 0)
  
  updatableDspVerbose.withValue(false) {
    jtagReset(stepSize)
    
    // write value 0x08 to address 0x00
    jtagSend(BigInt("0010", 2), 4, false, true, stepSize)
    jtagSend(BigInt("0" * 32, 2), 32, true, false, stepSize)
    jtagSend(BigInt("0011", 2), 4, false, false, stepSize)
    jtagSend(BigInt("0" * 56 ++ "00001000", 2), 64, true, false, stepSize)
    jtagSend(BigInt("0001", 2), 4, false, false, stepSize)
  }
    
  step(100)
  peek(dut.outStream.bits.data)
  expect(dut.outStream.bits.data, 8)

  poke(dut.outStream.ready, 0)

  /*jtagReset(stepSize)
  jtagSend(BigInt("0010", 2), 4, false, true, stepSize)
  jtagSend(BigInt("0"*32, 2), 32, true, false, stepSize)
  jtagSend(BigInt("0011", 2), 4, false, false, stepSize)
  jtagSend(BigInt("0"*56 ++ "00011000", 2), 64, true, false, stepSize)
  jtagSend(BigInt("0001", 2), 4, false, false, stepSize)*/

  updatableDspVerbose.withValue(false) {
    
    // set start address for burst write to 0x08 and number of burst transactions to 2
    jtagSend(BigInt("0010", 2), 4, false, false, stepSize)
    jtagSend(BigInt("0" * 24 ++ "00001000", 2), 32, true, false, stepSize)
    jtagSend(BigInt("1000", 2), 4, false, false, stepSize)
    jtagSend(BigInt("0" * 6 ++ "10", 2), 8, true, false, stepSize)
    
    // set data value for the first burst write transaction to 0x18
    jtagSend(BigInt("1010", 2), 4, false, false, stepSize)
    jtagSend(BigInt("0" * 6 ++ "00", 2), 8, true, false, stepSize)
    jtagSend(BigInt("1011", 2), 4, false, false, stepSize)
    jtagSend(BigInt("0" * 56 ++ "00011000", 2), 64, true, false, stepSize)
    
    // set data value for the second burst write transaction to 0x00
    jtagSend(BigInt("1010", 2), 4, false, false, stepSize)
    jtagSend(BigInt("0" * 6 ++ "01", 2), 8, true, false, stepSize)
    jtagSend(BigInt("1011", 2), 4, false, false, stepSize)
    jtagSend(BigInt("0" * 56 ++ "00000000", 2), 64, true, false, stepSize)
    
    // initiate burst write transactions
    jtagSend(BigInt("1001", 2), 4, false, false, stepSize)
  
    poke(dut.ioJTAG.jtag.TCK, 0)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 1)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 0)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 1)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 0)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 1)
    step(stepSize)
    poke(dut.ioJTAG.jtag.TCK, 0)
    step(stepSize)

    jtagSend(BigInt("0100", 2), 4, false, false, stepSize)

    var i = 0
    while (i < 32) {
      poke(dut.ioJTAG.jtag.TCK, 1)
      step(stepSize)
      poke(dut.ioJTAG.jtag.TCK, 0)
      step(stepSize)
      poke(dut.ioJTAG.jtag.TCK, 1)
      step(stepSize)
      poke(dut.ioJTAG.jtag.TCK, 0)
      step(stepSize)
      poke(dut.ioJTAG.jtag.TCK, 1)
      step(stepSize)
      poke(dut.ioJTAG.jtag.TCK, 0)
      step(stepSize)
      i += 1
    }
  }

  poke(dut.outStream.ready, 1)
  step(1)
  step(1)
  peek(dut.outStream.bits.data)
  expect(dut.outStream.bits.data, 24)

  updatableDspVerbose.withValue(false) {
  
    // set start address for burst read to 0x00 and number of burst transactions to 3
    jtagSend(BigInt("0010", 2), 4, false, false, stepSize)
    jtagSend(BigInt("0" * 32, 2), 32, true, false, stepSize)
    jtagSend(BigInt("1000", 2), 4, false, false, stepSize)
    jtagSend(BigInt("0" * 6 ++ "11", 2), 8, true, false, stepSize)
    
    // initiate burst read transactions
    jtagSend(BigInt("1100", 2), 4, false, false, stepSize)
    var i = 0
    while (i < 32 * 4) {
      poke(dut.ioJTAG.jtag.TCK, 1)
      step(stepSize)
      poke(dut.ioJTAG.jtag.TCK, 0)
      step(stepSize)
      poke(dut.ioJTAG.jtag.TCK, 1)
      step(stepSize)
      poke(dut.ioJTAG.jtag.TCK, 0)
      step(stepSize)
      poke(dut.ioJTAG.jtag.TCK, 1)
      step(stepSize)
      poke(dut.ioJTAG.jtag.TCK, 0)
      step(stepSize)
      i += 1
    }
  }

  step(300)
}

class Jtag2AXI4MultiplexerSpec extends FlatSpec with Matchers {
  implicit val p: Parameters = Parameters.empty

  val irLength = 4
  val initialInstruction = BigInt("0", 2)
  val addresses = AddressSet(0x00000, 0xffff)
  val beatBytes = 8
  val maxBurstNum = 8

  it should "Test JTAG To AXI4 Multiplexer" in {
    val lazyDut =
      LazyModule(new Jtag2AXI4Multiplexer(irLength, initialInstruction, beatBytes, addresses, maxBurstNum) {})

    //chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv"), () => lazyDut.module) { c =>
    chisel3.iotesters.Driver.execute(Array("-tbn", "verilator"), () => lazyDut.module) { c =>
      new Jtag2AXI4MultiplexerTester(lazyDut)
    } should be(true)
  }
}
