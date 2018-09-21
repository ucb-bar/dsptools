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

    // println(chisel3.Driver.emit(() => dut().module))
    // println(chisel3.Driver.emitVerilog(dut().module))

    chisel3.iotesters.Driver.execute(Array("-tbn", "firrtl", "-fiwv"), dut) {
      c => new AXI4PassthroughTester(lazymod)
    } should be (true)
  }

  it should "work with APB" in {
    val params = PassthroughParams(depth = 5)
    val lazymod = LazyModule(new APBPassthrough(params) with APBStandaloneBlock)
    val dut = () => lazymod.module

    //println(chisel3.Driver.emit(dut))
    // println(chisel3.Driver.emitVerilog(dut()))

    chisel3.iotesters.Driver.execute(Array("-tbn", "firrtl", "-fiwv"), dut) {
      c => new APBPassthroughTester(lazymod)
    } should be (true)
  }

  it should "work with TL" in {
    val params = PassthroughParams(depth = 5)
    val lazymod = LazyModule(new TLPassthrough(params) with TLStandaloneBlock)
    val dut = () => lazymod.module

    //println(chisel3.Driver.emit(dut))
    //println(chisel3.Driver.emitVerilog(dut()))

    chisel3.iotesters.Driver.execute(Array("-tbn", "verilator", "-fiwv"), dut) {
      c => new TLPassthroughTester(lazymod)
    } should be (true)
  }

  behavior of "Byte Rotate"

  it should "work with AXI4" in {
    val lazymod = LazyModule(new AXI4ByteRotate() with AXI4StandaloneBlock)
    val dut = () => lazymod.module

    //println(chisel3.Driver.emit(dut))
    //println(chisel3.Driver.emitVerilog(dut()))

    chisel3.iotesters.Driver.execute(Array(/*"-tiv",*/ "-tbn", "firrtl", "-fiwv"), dut) {
      c => new AXI4ByteRotateTester(lazymod)
    } should be (true)
  }

  it should "work with APB" in {
    val lazymod = LazyModule(new APBByteRotate() with APBStandaloneBlock)
    val dut = () => lazymod.module

    //println(chisel3.Driver.emit(dut))
    //println(chisel3.Driver.emitVerilog(dut()))

    chisel3.iotesters.Driver.execute(Array("-tbn", "firrtl", "-fiwv"), dut) {
      c => new APBByteRotateTester(lazymod)
    } should be (true)

  }

  it should "work with TL" in {
    val lazymod = LazyModule(new TLByteRotate() with TLStandaloneBlock)
    val dut = () => lazymod.module

    //println(chisel3.Driver.emit(dut))
    //println(chisel3.Driver.emitVerilog(dut()))

    chisel3.iotesters.Driver.execute(Array("-tbn", "verilator", "-fiwv"), dut) {
      c => new TLByteRotateTester(lazymod)
    } should be (true)
  }
}

/*

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

class DspBlockTesterSpec extends FlatSpec with Matchers {
  val manager = new TesterOptionsManager {
    testerOptions = TesterOptions(backendName = "verilator", testerSeed = 7L, isVerbose = true)
    interpreterOptions = InterpreterOptions(setVerbose = false, writeVCD = true)
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
