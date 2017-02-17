// See LICENSE for license details.

package dspblocks

import cde._
import chisel3.iotesters._
import craft._
import diplomacy._
import dsptools._
import firrtl_interpreter.InterpreterOptions
import org.scalatest._
import sam._

class PassthroughTester(dut: PassthroughModule) extends DspBlockTester(dut) {
  pauseStream()

  val streamIn = Seq((0 until 12).map(x=>BigInt(x)).toSeq)

  // Check that status registers can be read
  val readUUID = axiRead(addrmap("uuid"))
  require(readUUID == dut.hashCode,
    s"UUID from SCR incorrect, was $readUUID, should be ${dut.hashCode}")
  val readDelay = axiRead(addrmap("delay"))
  require(readDelay == dut.passthroughDelay,
          s"Delay from SCR incorrect, was $readDelay, should be ${dut.passthroughDelay}")

  // Run stream through
  playStream()
  step(5)
  pauseStream()
  step(5)
  playStream()
  step(100)


  streamOut.last.zip(streamIn.last).foreach {case (out, in) =>
    require(out == in, s"out ($out) was not the same as in ($in)")
  }
}

class BarrelShifterTester(dut: BarrelShifterModule) extends DspBlockTester(dut) {
  pauseStream()

  val subTestLength = 12
  val streamIn = Seq.fill(3)((0 until subTestLength).map(x => BigInt(x)).toSeq)

  val readUUID = axiRead(addrmap("uuid"))
  require(readUUID == dut.hashCode,
          s"UUID from SCR incorrect, was $readUUID, should be ${dut.hashCode}")

  playStream()
  step(subTestLength)
  pauseStream()

  axiWrite(addrmap("shiftBy"), 1)
  playStream()
  step(subTestLength)
  pauseStream()

  axiWrite(addrmap("shiftBy"), 3)
  playStream()
  step(subTestLength)

  val toCheck = streamOut.drop(streamOut.length - 3)

  println(s"Result was ${toCheck.toString}")

  toCheck(0).zip(streamIn(0)).foreach { case(out, in) =>
    require (out == 1 * in, s"out ($out) did not match in ($in)")
  }
  toCheck(1).zip(streamIn(1)).foreach { case(out, in) =>
    require (out == 2 * in, s"out ($out) did not match in ($in)")
  }
  toCheck(2).zip(streamIn(2)).foreach { case(out, in) =>
    require (out == 8 * in, s"out ($out) did not match in ($in)")
  }
}

class PTBSTester(dut: DspChainModule) extends DspChainTester(dut) {
  def streamIn = Seq((0 until 12).map(x=>BigInt(x)).toSeq)
}

class DspBlockTesterSpec extends FlatSpec with Matchers {
  val manager = new TesterOptionsManager {
    testerOptions = TesterOptions(backendName = "verilator", testerSeed = 7L, isVerbose = true)
    interpreterOptions = InterpreterOptions(setVerbose = false, writeVCD = false)
  }


  behavior of "DspBlockTester"

  it should "work with Passthrough" in {
    implicit val p: Parameters = Parameters.root((
      ConfigBuilder.dspBlockParams("passthrough", 12) ++
      ConfigBuilder.buildDSP("passthrough", q => new Passthrough()(q)))
        .toInstance)
        .alterPartial({
          case PassthroughDelay => 10
        })
    val dut = () => {
      val lazyPassthrough = LazyModule(new Passthrough)
      lazyPassthrough.module
    }
    chisel3.iotesters.Driver.execute(dut, manager) { c => new PassthroughTester(c) } should be (true)
  }

  it should "work with BarrelShifter" in {
    implicit val p: Parameters = Parameters.root((
      ConfigBuilder.dspBlockParams("passthrough", 12) ++
      ConfigBuilder.buildDSP("passthrough", q => new BarrelShifter()(q)))
        .toInstance)
    val dut = () => {
      val lazyBarrelShifter = LazyModule(new BarrelShifter)
      lazyBarrelShifter.module
    }
    chisel3.iotesters.Driver.execute(dut, manager) { c => new BarrelShifterTester(c) } should be (true)
  }

  behavior of "DspChainTester"

  it should "work with Passthrough + BarrelShifter" in {
    import chisel3._
    implicit val p: Parameters = Parameters.root((
      new Config(
        (pname, site, here) => pname match {
          case SAMKey => SAMConfig(16, 16, 16)
          case DspChainId => "chain"
          case DspChainKey("chain") => DspChainParameters(
            blocks = Seq(
              ({p: Parameters => new Passthrough()(p)}, "chain:passthrough"),
              ({p: Parameters => new BarrelShifter()(p)}, "chain:barrelshifter")
            ),
            logicAnalyzerSamples = 256,
            logicAnalyzerUseCombinationalTrigger = true,
            patternGeneratorSamples = 256,
            patternGeneratorUseCombinationalTrigger = true
          )
          case PassthroughDelay => 10
          case _ => throw new CDEMatchError
        }
        ) ++
      ConfigBuilder.dspBlockParams("chain:passthrough", 12) ++
      ConfigBuilder.buildDSP("chain:passthrough", q => new Passthrough()(q)) ++
      ConfigBuilder.dspBlockParams("chain:barrelshifter", 12) ++
      ConfigBuilder.buildDSP("chain:barrelshifter", q => new BarrelShifter()(q))
    ).toInstance)

    val dut = () => {
      val lazyChain = LazyModule(new DspChain(0x0000, 0x1000))
      lazyChain.module
    }

    chisel3.iotesters.Driver.execute( dut, manager) {
      c => new PTBSTester(c) }

  }


  it should "work with BarrelShifter + Passthrough" in {}
}

