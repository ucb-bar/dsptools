// SPDX-License-Identifier: Apache-2.0

package freechips.rocketchip.jtag2mm

import dsptools.DspTester
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class JtagFuzzerTester(dut: JtagFuzzer) extends DspTester(dut) {

  step(10)
  step(5)
  step(2500)
}


class JtagFuzzerSpec extends AnyFlatSpec with Matchers {

  def dut(irLength: Int, beatBytes: Int, numOfTransfers: Int): () => JtagFuzzer = () => {
    new JtagFuzzer(irLength, beatBytes, numOfTransfers)
  }

  val beatBytes = 4
  val irLength = 4
  val numOfTransfers = 10
  
  it should "Test JTAG Fuzzer" in {

    //chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv"), () => dut) { c =>
    chisel3.iotesters.Driver.execute(Array("-tbn", "verilator"), dut(irLength, beatBytes, numOfTransfers)) { c =>
      new JtagFuzzerTester(c)
    } should be(true)
  }
}
