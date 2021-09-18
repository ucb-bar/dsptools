// SPDX-License-Identifier: Apache-2.0

package freechips.rocketchip.jtag2mm

import chisel3._
import chisel3.util._
import chisel3.experimental._
//import chisel3.experimental.{withClockAndReset}

import dsptools._
import dsptools.numbers._

import dspblocks._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._


abstract class TestMultiplexer[D, U, EO, EI, B <: Data]()(implicit p: Parameters)
    extends DspBlock[D, U, EO, EI, B] with HasCSR {
    
  val streamNode = AXI4StreamMasterNode(Seq(AXI4StreamMasterPortParameters(Seq(AXI4StreamMasterParameters("out", n = 8)))))

  lazy val module = new LazyModuleImp(this) {
    val (out, _) = streamNode.out.unzip

    val a = RegInit(UInt(64.W), 0.U)
    val b = RegInit(UInt(64.W), 0.U)
    val select = RegInit(Bool(), true.B)

    regmap(0x00 -> Seq(RegField(64, a)), 0x08 -> Seq(RegField(64, b)), 0x10 -> Seq(RegField(1, select)))

    out.head.bits.data := Mux(select, a, b)
  }
}

class TLMultiplexer()(implicit p: Parameters)
    extends TestMultiplexer[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle]()
    with TLBasicBlock
    
class Jtag2TLMultiplexer(
  irLength:           Int,
  initialInstruction: BigInt,
  beatBytes:          Int,
  jtagAddresses:      AddressSet,
  maxBurstNum:        Int)
    extends LazyModule()(Parameters.empty) {

  val multiplexerModule = LazyModule(new TLMultiplexer() {

    def standaloneParams = TLBundleParameters(
      addressBits = 16,
      dataBits = 64,
      sourceBits = 16,
      sinkBits = 16,
      sizeBits = 3,
      echoFields = Nil,
      requestFields = Nil,
      responseFields = Nil,
      hasBCE = false
    )

    val clientParams = TLClientParameters(
      name = "BundleBridgeToTL",
      sourceId = IdRange(0, 1),
      nodePath = Seq(),
      requestFifo = false,
      visibility = Seq(AddressSet(0, ~0)),
      supportsProbe = TransferSizes(1, beatBytes),
      supportsArithmetic = TransferSizes(1, beatBytes),
      supportsLogical = TransferSizes(1, beatBytes),
      supportsGet = TransferSizes(1, beatBytes),
      supportsPutFull = TransferSizes(1, beatBytes),
      supportsPutPartial = TransferSizes(1, beatBytes),
      supportsHint = TransferSizes(1, beatBytes)
    )

    val ioMem = mem.map { m =>
      {
        val ioMemNode = BundleBridgeSource(() => TLBundle(standaloneParams))
        m := BundleBridgeToTL(TLClientPortParameters(Seq(clientParams))) := ioMemNode
        val ioMem = InModuleBody { ioMemNode.makeIO() }
        ioMem
      }
    }

    val ioStreamNode = BundleBridgeSink[AXI4StreamBundle]()
    ioStreamNode :=
      AXI4StreamToBundleBridge(AXI4StreamSlaveParameters()) := streamNode
    val outStream = InModuleBody { ioStreamNode.makeIO() }
  })

  val jtagModule = LazyModule(
    new TLJTAGToMasterBlock(irLength, initialInstruction, beatBytes, jtagAddresses, maxBurstNum)
  )

  InModuleBody { multiplexerModule.ioMem.get <> jtagModule.ioTL }

  def makeIO1(): AXI4StreamBundle = {
    val io2: AXI4StreamBundle = IO(multiplexerModule.outStream.cloneType)
    io2.suggestName("outStream")
    io2 <> multiplexerModule.outStream
    io2
  }
  def makeIO2(): TopModuleIO = {
    val io2: TopModuleIO = IO(jtagModule.ioJTAG.cloneType)
    io2.suggestName("ioJTAG")
    io2 <> jtagModule.ioJTAG
    io2
  }

  val outStream = InModuleBody { makeIO1() }
  val ioJTAG = InModuleBody { makeIO2() }

  lazy val module = new LazyModuleImp(this)
}

class AXI4Multiplexer()(implicit p: Parameters)
    extends TestMultiplexer[AXI4MasterPortParameters, AXI4SlavePortParameters, AXI4EdgeParameters, AXI4EdgeParameters, AXI4Bundle]
    with AXI4BasicBlock
    
class Jtag2AXI4Multiplexer(
  irLength:           Int,
  initialInstruction: BigInt,
  beatBytes:          Int,
  jtagAddresses:      AddressSet,
  maxBurstNum:        Int)
    extends LazyModule()(Parameters.empty) {

  val multiplexerModule = LazyModule(new AXI4Multiplexer() {

    def standaloneParams = AXI4BundleParameters(addrBits = 8, dataBits = beatBytes*8, idBits = 1)
    val ioMem = mem.map { 
      m => {
        val ioMemNode = BundleBridgeSource(() => AXI4Bundle(standaloneParams))
        m := BundleBridgeToAXI4(AXI4MasterPortParameters(Seq(AXI4MasterParameters("bundleBridgeToAXI4")))) := ioMemNode
        val ioMem = InModuleBody { ioMemNode.makeIO() }
        ioMem
      }
    }

    val ioStreamNode = BundleBridgeSink[AXI4StreamBundle]()
    ioStreamNode :=
      AXI4StreamToBundleBridge(AXI4StreamSlaveParameters()) := streamNode
    val outStream = InModuleBody { ioStreamNode.makeIO() }
  })

  val jtagModule = LazyModule(
    new AXI4JTAGToMasterBlock(irLength, initialInstruction, beatBytes, jtagAddresses, maxBurstNum)
  )

  InModuleBody { multiplexerModule.ioMem.get <> jtagModule.ioAXI4 }

  def makeIO1(): AXI4StreamBundle = {
    val io2: AXI4StreamBundle = IO(multiplexerModule.outStream.cloneType)
    io2.suggestName("outStream")
    io2 <> multiplexerModule.outStream
    io2
  }
  def makeIO2(): TopModuleIO = {
    val io2: TopModuleIO = IO(jtagModule.ioJTAG.cloneType)
    io2.suggestName("ioJTAG")
    io2 <> jtagModule.ioJTAG
    io2
  }

  val outStream = InModuleBody { makeIO1() }
  val ioJTAG = InModuleBody { makeIO2() }

  lazy val module = new LazyModuleImp(this)
}

/*object JTAGToTLMultiplexerApp extends App {

  val irLength = 4
  val initialInstruction = BigInt("0", 2)
  val addresses = AddressSet(0x00000, 0x3fff)
  val beatBytes = 8
  val maxBurstNum = 8

  implicit val p: Parameters = Parameters.empty
  val appModule = LazyModule(
    new Jtag2TLMultiplexer(irLength, initialInstruction, beatBytes, addresses, maxBurstNum)
  )

  chisel3.Driver.execute(args, () => appModule.module)
}

object JTAGToAXI4MultiplexerApp extends App {

  val irLength = 4
  val initialInstruction = BigInt("0", 2)
  val addresses = AddressSet(0x00000, 0x3fff)
  val beatBytes = 8
  val maxBurstNum = 8

  implicit val p: Parameters = Parameters.empty
  val appModule = LazyModule(
    new Jtag2AXI4Multiplexer(irLength, initialInstruction, beatBytes, addresses, maxBurstNum)
  )

  chisel3.Driver.execute(args, () => appModule.module)
}*/
