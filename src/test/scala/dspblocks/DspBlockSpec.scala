// See LICENSE for license details.

package dspblocks

import chisel3._
import chisel3.experimental._
import dsptools.numbers.DspComplex

import freechips.rocketchip.amba.apb._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.coreplex.BaseCoreplexConfig
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

import ofdm.{AutocorrBlind, AutocorrParams}
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.Seq

class DspBlockSpec extends FlatSpec with Matchers {
  implicit val p: Parameters = Parameters.root((new BaseCoreplexConfig).toInstance)

  behavior of "Blind Wrapper"

  it should "wrap an autocorr" in {



    val params = AutocorrParams(
      DspComplex(FixedPoint(32.W, 20.BP), FixedPoint(32.W, 20.BP)),
      //DspComplex(FixedPoint(8.W, 4.BP), FixedPoint(8.W, 4.BP)),
      // genOut=Some(DspComplex(FixedPoint(16.W, 8.BP), FixedPoint(16.W, 8.BP))),
      maxApart = 32,
      maxOverlap = 161,
      address = AddressSet(0x0, 0xffffffffL),
      beatBytes = 8)
    val inWidthBytes = 8 //(params.genIn.getWidth + 7) / 8
    val outWidthBytes = 8 //params.genOut.map(x => (x.getWidth + 7)/8).getOrElse(inWidthBytes)

    println(s"In bytes = $inWidthBytes and out bytes = $outWidthBytes")

    val blindNodes = DspBlockBlindNodes(
      streamIn  = () => AXI4StreamMasterNode(Seq(AXI4StreamMasterPortParameters())),
      streamOut = () => AXI4StreamSlaveNode(Seq(AXI4StreamSlavePortParameters())),
      mem       = () => AXI4IdentityNode()
      )

    // println(chisel3.Driver.emit(() => LazyModule(AutocorrBlind(params, blindNodes)).module))
    println(chisel3.Driver.emitVerilog(LazyModule(AutocorrBlind(params, blindNodes)).module))

  }

  behavior of "Passthrough"

  it should "work with AXI4" in {
    val params = PassthroughParams(depth = 5)
    val blindNodes = DspBlockBlindNodes.apply(
      AXI4StreamBundleParameters(
        n = 8,
        i = 1,
        d = 1,
        u = 1,
        hasData = true,
        hasStrb = true,
        hasKeep = true
      ),
      () => AXI4MasterNode(Seq(AXI4MasterPortParameters(Seq(AXI4MasterParameters("passthrough")))))
    )

    val dut = () => LazyModule(DspBlock.blindWrapper(() => new AXI4Passthrough(params), blindNodes)).module

    //println(chisel3.Driver.emit(dut))
    //println(chisel3.Driver.emitVerilog(dut()))

    chisel3.iotesters.Driver.execute(Array("-tbn", "verilator", "-fiwv"), dut) {
      c => new AXI4PassthroughTester(c)
    } should be (true)
  }

  it should "work with APB" in {
    val params = PassthroughParams(depth = 5)
    val blindNodes = DspBlockBlindNodes(
      AXI4StreamBundleParameters(
        n = 8,
        i = 1,
        d = 1,
        u = 1,
        hasData = true,
        hasKeep = true,
        hasStrb = true
      ),
      mem = () => APBMasterNode(Seq(APBMasterPortParameters(Seq(
        APBMasterParameters(
          "passthrough"
        ))))))

    val dut = () => LazyModule(DspBlock.blindWrapper( () => new APBPassthrough(params), blindNodes)).module

    //println(chisel3.Driver.emit(dut))
    //println(chisel3.Driver.emitVerilog(dut()))

    chisel3.iotesters.Driver.execute(Array("-tbn", "firrtl", "-fiwv"), dut) {
      c => new APBPassthroughTester(c)
    } should be (true)

  }

  it should "work with TL" in {
    val params = PassthroughParams(depth = 5)
    val blindNodes = DspBlockBlindNodes(
      AXI4StreamBundleParameters(
      n = 8,
      i = 1,
      d = 1,
      u = 1,
      hasData = true,
      hasKeep = true,
      hasStrb = true
    ),
      mem = () => TLClientNode(
        Seq(TLClientPortParameters(
        Seq(TLClientParameters("passthrough"))))))

    val dut = () => LazyModule(DspBlock.blindWrapper( () => new TLPassthrough(params), blindNodes)).module

    //println(chisel3.Driver.emit(dut))
    //println(chisel3.Driver.emitVerilog(dut()))

    chisel3.iotesters.Driver.execute(Array("-tbn", "firrtl", "-fiwv"), dut) {
      c => new TLPassthroughTester(c)
    } should be (true)

  }
}

/*
import cde._
import chisel3.iotesters._
import craft._
import diplomacy._
import dsptools._
import firrtl_interpreter.InterpreterOptions
import jtag._
import org.scalatest._
import sam._
import testchipip._

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

class PTBSTester(dut: DspChainWithAXI4SInputModule) extends DspChainTester(dut) {
  val streamIn = Seq((0 until 48).map(x=>BigInt(x)).toSeq)
  pauseStream()

  for ( (mod, map) <- SCRAddressMap.contents) {
    println(s"Module: $mod")
    for ( (reg, addr) <- map ) {
      println(s"\t$reg\t@\t$addr")
    }
  }
  val first_uuid_addr = SCRAddressMap("chain:passthrough").get("uuid")
  val second_uuid_addr = SCRAddressMap("chain:barrelshifter").get("uuid")
  println(s"first uuid addr is $first_uuid_addr")
  println(s"second uuid addr is $second_uuid_addr")
  addrmap.keys.foreach(x => println(x))
  println(s"${addrmap("chain:passthrough:uuid")} is address of uuid")
  val readPassthroughUUID = axiRead(addrmap("chain:passthrough:uuid"))
  step(10)
  require(readPassthroughUUID == dut.modules.head.hashCode,
    s"UUID from SCR incorrect, was $readPassthroughUUID, should be ${dut.modules.head.hashCode}")
  println("Passthrough UUID correct")
  val readBarrelShifterUUID = axiRead(addrmap("chain:barrelshifter:uuid"))
  require(readBarrelShifterUUID == dut.modules.tail.head.hashCode,
    s"UUID from SCR incorrect, was $readBarrelShifterUUID, should be ${dut.modules.tail.head.hashCode}")
  println("Barrel Shifter UUID correct")

  val barrelshifter_shift_addr = addrmap("chain:barrelshifter:shiftBy")
  val shiftBy = 3
  axiWrite(barrelshifter_shift_addr, shiftBy + 3)
  val readWrongBarrelShifterShift = axiRead(barrelshifter_shift_addr)
  require(readWrongBarrelShifterShift == shiftBy + 3,
    s"Shift should have been ${shiftBy+3} after writing, was $readWrongBarrelShifterShift")
  axiWrite(barrelshifter_shift_addr, shiftBy)
  val readBarrelShifterShift = axiRead(barrelshifter_shift_addr)
  require(readBarrelShifterShift == shiftBy,
    s"Shift should have been $shiftBy after writing, was $readBarrelShifterShift")
  println("Shift was written correctly")

  axi = ctrlAXI

  initiateSamCapture(streamIn.head.length, waitForSync = true)

  playStream()
  println("Playing")
  for (i <- 0 until 100) {
    println(s"stream_in had valid=${peek(dut.io.stream_in.valid)} and bits=${peek(dut.io.stream_in.bits)}")
    dut.modules.map(mod =>  {
      val valid = peek(mod.io.in.valid)
      val bits  = peek(mod.io.in.bits)
      println(s"${mod.name} input had valid=$valid and bits=$bits")
    })
    dut.modules.map(mod =>  {
      val valid = peek(mod.io.out.valid)
      val bits  = peek(mod.io.out.bits)
      println(s"${mod.name} output had valid=$valid and bits=$bits")
    })
    step(1)
  }

  println("Getting output")
  val samOut = getOutputFromSam()
}

class PGLAPTBSTester(dut: DspChainWithAXI4SInputModule) extends DspChainTester(dut) with PGLATester[DspChainWithAXI4SInputModule] {
  val streamIn = Seq((0 until 48).map(x=>BigInt(x)).toSeq)
  pauseStream()

  val pgData = Seq(BigInt(5), BigInt(6), BigInt(7), BigInt(9))

  initiateSamCapture(7, waitForSync = true)
  enablePatternGenerator(pgData)

  playStream()
  println("Playing")
  step(30)

  println("Getting output")
  val samOut = getOutputFromSam() // .foreach(x => println(x.toString))
  samOut.zipWithIndex.foreach { case (x, idx) =>
    println(s"Output $idx is $x")
    require(x == pgData(idx % pgData.length))
  }
}

object chainParameters {
  val defaultSAMConfig = SAMConfig(16, 16)

  def apply(
    passthroughConnect: BlockConnectionParameters, 
    barrelshiftConnect: BlockConnectionParameters): Parameters = Parameters.root((
    new Config(
      (pname, site, here) => pname match {
        case DspChainIncludeJtag => true
        case DspChainAPIDirectory => "./"
        case DspChainAXI4SInputWidth => 128
        case DefaultSAMKey => defaultSAMConfig
        case DspChainId => "chain"
        case DspChainKey("chain") => DspChainParameters(
          blocks = Seq(
            ({p: Parameters => new Passthrough()(p)}, "chain:passthrough", passthroughConnect, Some(defaultSAMConfig)),
            ({p: Parameters => new BarrelShifter()(p)}, "chain:barrelshifter", barrelshiftConnect, Some(defaultSAMConfig))
          ),
          logicAnalyzerSamples = 256,
          logicAnalyzerUseCombinationalTrigger = true,
          patternGeneratorSamples = 256,
          patternGeneratorUseCombinationalTrigger = true
        )
        case PassthroughDelay => 10
        case IPXactParameters(id) => scala.collection.mutable.Map[String,String]()
        case _ => throw new CDEMatchError
      }
      ) ++
    ConfigBuilder.dspBlockParams("chain:passthrough", 128) ++
    ConfigBuilder.buildDSP("chain:passthrough", q => new Passthrough()(q)) ++
    ConfigBuilder.dspBlockParams("chain:barrelshifter", 128) ++
    ConfigBuilder.buildDSP("chain:barrelshifter", q => new BarrelShifter()(q))
  ).toInstance)

}

class DspBlockTesterSpec extends FlatSpec with Matchers {
  val manager = new TesterOptionsManager {
    testerOptions = TesterOptions(backendName = "verilator", testerSeed = 7L, isVerbose = true)
    interpreterOptions = InterpreterOptions(setVerbose = false, writeVCD = true)
  }


  behavior of "DspBlockTester"

  it should "work with Passthrough" ignore {
    SCRAddressMap.contents.clear

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

  it should "work with BarrelShifter" ignore {
    SCRAddressMap.contents.clear

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

  it should "work with Passthrough + BarrelShifter" ignore {
    SCRAddressMap.contents.clear

    implicit val p: Parameters = 
      chainParameters(BlockConnectEverything, BlockConnectEverything)

    val dut = () => {
      val lazyChain = LazyModule(new DspChainWithAXI4SInput())
      lazyChain.module
    }

    chisel3.iotesters.Driver.execute( dut, manager) {
      c => new PTBSTester(c) }

  }

  it should "work with some sams, etc. disabled" ignore {
    SCRAddressMap.contents.clear

    implicit val p: Parameters =
      chainParameters(BlockConnectSAMOnly, BlockConnectPGLAOnly)

    val dut = () => {
      val lazyChain = LazyModule(new DspChainWithAXI4SInput())
      lazyChain.module
    }
    chisel3.iotesters.Driver.execute( dut, manager) {
      c => new PTBSTester(c) }
  }



  behavior of "Pattern Generator + Logic Analyzer"

  it should "work with BarrelShifter + Passthrough" ignore {
    SCRAddressMap.contents.clear

    implicit val p: Parameters =
      chainParameters(BlockConnectEverything, BlockConnectEverything)

      val dut = () => {
        val lazyChain = LazyModule(new DspChainWithAXI4SInput())
        lazyChain.module
      }
    chisel3.iotesters.Driver.execute( dut, manager) {
      c => new PGLAPTBSTester(c) }
  }
}

*/