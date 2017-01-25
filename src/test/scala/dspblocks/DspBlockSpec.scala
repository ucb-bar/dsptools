// See LICENSE for license details.

package dspblocks

import cde._
import chisel3.iotesters._
import diplomacy._
import dsptools._
import firrtl_interpreter.InterpreterOptions
import org.scalatest._
import sam.LocalTest

class PassthroughTester(dut: Passthrough) extends DspBlockTester(dut) {
  pauseStream()

  def streamIn = (0 until 12).map(x=>BigInt(x)).toSeq

  require(axiRead(addrmap("uuid")) == dut.hashCode, "UUID from SCR incorrect")
  require(axiRead(addrmap("delay")) == dut.passthroughDelay, "Delay from SCR incorrect")

  playStream()

  poke(dut.io.in.sync, 1)
  step(1)
  poke(dut.io.in.sync, 0)
  step(100)

  streamOut.last.zip(streamIn).foreach {case (out, in) =>
    require(out == in, s"out ($out) was not the same as in ($in)")
  }
}

class BarrelShifterTester(dut: BarrelShifter) extends DspBlockTester(dut) {
  pauseStream()

  def streamIn = (0 until 12).map(x => BigInt(x)).toSeq

  require(axiRead(addrmap("uuid")) == dut.hashCode, "UUID from SCR incorrect")

  playStream()

  poke(dut.io.in.sync, 1)
  step(1)
  poke(dut.io.in.sync, 0)
  step(100)

  pauseStream()
  restartStream()
  axiWrite(addrmap("shiftBy"), 1)
  playStream()

  poke(dut.io.in.sync, 1)
  step(1)
  poke(dut.io.in.sync, 0)
  step(100)

  pauseStream()
  restartStream()
  axiWrite(addrmap("shiftBy"), 3)
  playStream()

  poke(dut.io.in.sync, 1)
  step(1)
  poke(dut.io.in.sync, 0)
  step(100)

  val toCheck = streamOut.drop(streamOut.length - 3)

  toCheck(0).zip(streamIn).foreach { case(out, in) =>
    require (out == 1 * in, s"out ($out) did not match in ($in)")
  }
  toCheck(1).zip(streamIn).foreach { case(out, in) =>
    require (out == 2 * in, s"out ($out) did not match in ($in)")
  }
  toCheck(2).zip(streamIn).foreach { case(out, in) =>
    require (out == 8 * in, s"out ($out) did not match in ($in)")
  }
}

class PTBSTester(dut: DspChain) extends DspChainTester(dut) {
}

class DspBlockTesterSpec extends FlatSpec with Matchers {
  val manager = new TesterOptionsManager {
    testerOptions = TesterOptions(backendName = "firrtl", testerSeed = 7L)
    interpreterOptions = InterpreterOptions(setVerbose = true, writeVCD = false)
  }


  behavior of "DspBlockTester"

  it should "work with Passthrough" in {
    implicit val p: Parameters = Parameters.root(new sam.DspConfig().toInstance).alterPartial({
      case BaseAddr => 0
      case PassthroughDelay => 10
    })
    val dut = () => {
      val lazyPassthrough = LazyModule(new LazyPassthrough)
      lazyPassthrough.module
    }
    chisel3.iotesters.Driver.execute(dut, manager) { c => new PassthroughTester(c) } should be (true)
  }

  it should "work with BarrelShifter" in {
    implicit val p: Parameters = Parameters.root(new sam.DspConfig().toInstance).alterPartial({
      case BaseAddr => 0
      case DspBlockKey => DspBlockParameters(64, 64)
    })
    val dut = () => {
      val lazyBarrelShifter = LazyModule(new LazyBarrelShifter)
      lazyBarrelShifter.module
    }
    chisel3.iotesters.Driver.execute(dut, manager) { c => new BarrelShifterTester(c) } should be (true)
  }
  
  behavior of "DspChainTester"

  it should "work with Passthrough + BarrelShifter" ignore {
    import chisel3._
    implicit val p: Parameters = Parameters.root(new sam.DspConfig().toInstance).alterPartial({
      case BaseAddr => 0
      case PassthroughDelay => 10
      case DspBlockId => "block"
      case DspBlockKey("block") => DspBlockParameters(64, 64)
      case GenKey("block") => new GenParameters {
        def lanesIn = 1
        def genIn[V<:Data] = UInt(64.W).asInstanceOf[V]
      }
      case DspChainId => "chain"
      case DspChainKey("chain") => DspChainParameters(
        blocks = Seq(
          ({implicit p: Parameters => new LazyPassthrough}, "block"),
          ({implicit p: Parameters => new LazyBarrelShifter}, "block")
        ),
        dataBaseAddr = 0x0000,
        ctrlBaseAddr = 0x1000
      )
    })

    chisel3.iotesters.Driver.execute( () => new DspChain, manager) {
      c => new PTBSTester(c) }
  
  }

  it should "work with BarrelShifter + Passthrough" in {}
}

