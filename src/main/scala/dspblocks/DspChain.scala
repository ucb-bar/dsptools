// See LICENSE for license details

package dspblocks

import cde._
import chisel3._
import craft._
import debuggers._
import _root_.junctions._
import diplomacy._
import dspjunctions._
import sam._
import testchipip._
import uncore.converters._
import uncore.tilelink._
import junctions._
import rocketchip._

object DspChain {
  private[dspblocks] val _addrMaps: scala.collection.mutable.Map[String, AddrMap] =
    scala.collection.mutable.Map()

  def addrMap(id: String): AddrMap = _addrMaps(id)

  def addrMapIds(): Seq[String] = _addrMaps.keys.toSeq

}

case class DspChainParameters (
  val blocks: Seq[(Parameters => DspBlock, String)],
  logicAnalyzerSamples: Int,
  logicAnalyzerUseCombinationalTrigger: Boolean,
  patternGeneratorSamples: Int,
  patternGeneratorUseCombinationalTrigger: Boolean,
  biggestWidth: Int = 128
)

case object DspChainId extends Field[String]
case class DspChainKey(id: String) extends Field[DspChainParameters]

trait HasDspChainParameters extends HasSCRParameters {
  val p: Parameters
  val dspChainExternal                        = p(DspChainKey(p(DspChainId)))
  val id                                      = p(DspChainId)
  val blocks                                  = dspChainExternal.blocks
  val logicAnalyzerSamples                    = dspChainExternal.logicAnalyzerSamples
  val logicAnalyzerUseCombinationalTrigger    = dspChainExternal.logicAnalyzerUseCombinationalTrigger
  val patternGeneratorSamples                 = dspChainExternal.patternGeneratorSamples
  val patternGeneratorUseCombinationalTrigger = dspChainExternal.patternGeneratorUseCombinationalTrigger

  val biggestWidth                            = dspChainExternal.biggestWidth
  // find biggest IO
  /*val biggestWidth = mod_ios.foldLeft(0){
    case(maximum, io) =>
      math.max(maximum,
        math.max(io.in.bits.getWidth, io.out.bits.getWidth))
  }*/
  val wordWidth = scrDataBits
  val dataWidthWords = (biggestWidth + wordWidth-1) / wordWidth
}

trait HasDecoupledSCR {
  /*
   * Helper for creating SCR interface for Decoupled IOs
   * Inputs are strings that look up entries in the scr file
   * Returns a pair of Bools. The first is valid and the second is finished
   */
  def decoupledHelper(valid: Bool, ready: Bool, writeEn: Bool, writeFinishedStatus: UInt) = {
    val writeFinished = Reg(init=false.B)
    val prevWriteEn = Reg(next=writeEn)
    when (!writeEn) {
      writeFinished := false.B
    }
    when (writeEn && !prevWriteEn) {
      writeFinished := false.B
    }
    when (writeEn && ready) {
      writeFinished := true.B
    }
    writeFinishedStatus := writeFinished
    valid := writeEn && !writeFinished
    ()
  }
}

trait HasPatternGenerator extends HasDspChainParameters {
  def scrbuilder: SCRBuilder

  scrbuilder.addControl("patternGeneratorEnable")
  scrbuilder.addControl("patternGeneratorSelect")
  scrbuilder.addControl("patternGeneratorReadyBypass")
  scrbuilder.addControl("patternGeneratorTriggerMode")
  scrbuilder.addControl("patternGeneratorLastSample")
  scrbuilder.addControl("patternGeneratorContinuous")
  scrbuilder.addControl("patternGeneratorArm")
  scrbuilder.addControl("patternGeneratorAbort")
  scrbuilder.addControl("patternGeneratorControlEnable")
  scrbuilder.addStatus("patternGeneratorControlFinished")
  scrbuilder.addControl("patternGeneratorWriteAddr")

  for (i <- 0 until dataWidthWords) {
    scrbuilder.addControl(s"patternGeneratorWriteData_$i")
  }
  scrbuilder.addControl("patternGeneratorWriteEnable")
  scrbuilder.addStatus("patternGeneratorWriteFinished")
  scrbuilder.addStatus("patternGeneratorState")
  scrbuilder.addStatus("patternGeneratorNumSampled")
  scrbuilder.addStatus("patternGeneratorStarted")
  scrbuilder.addStatus("patternGeneratorOverflow")
}

trait HasLogicAnalyzer extends HasDspChainParameters {
  def scrbuilder: SCRBuilder

  scrbuilder.addControl("logicAnalyzerEnable")
  scrbuilder.addControl("logicAnalyzerSelect")
  scrbuilder.addControl("logicAnalyzerValidBypass")
  scrbuilder.addControl("logicAnalyzerTriggerMode")
  scrbuilder.addControl("logicAnalyzerNumSamples")
  scrbuilder.addControl("logicAnalyzerArm")
  scrbuilder.addControl("logicAnalyzerAbort")
  scrbuilder.addControl("logicAnalyzerControlEnable")
  scrbuilder.addStatus("logicAnalyzerControlFinished")
  scrbuilder.addControl("logicAnalyzerWriteAddr")

  for (i <- 0 until dataWidthWords) {
    scrbuilder.addStatus(s"logicAnalyzerWriteData_$i")
  }
  scrbuilder.addControl("logicAnalyzerWriteEnable")
  scrbuilder.addStatus("logicAnalyzerWriteFinished")
  scrbuilder.addStatus("logicAnalyzerState")
  scrbuilder.addStatus("logicAnalyzerNumSampled")
  scrbuilder.addStatus("logicAnalyzerOverflow")
}

trait HasPatternGeneratorModule extends HasDspChainParameters with HasDecoupledSCR {
  def scrfile: SCRFile
  def dataWidthWords: Int
  def wordWidth: Int

  val patternGenerator = Module(new PatternGenerator(
                                  dataWidthWords*wordWidth, 1,
                                  patternGeneratorSamples,
                                  patternGeneratorUseCombinationalTrigger)
  )

  scrfile.status("patternGeneratorState")      :=
    patternGenerator.io.status.state
  scrfile.status("patternGeneratorNumSampled") :=
    patternGenerator.io.status.numSampled
  scrfile.status("patternGeneratorStarted")    :=
    patternGenerator.io.status.started
  scrfile.status("patternGeneratorOverflow")   :=
    patternGenerator.io.status.overflow

  patternGenerator.io.memory.bits.writeAddr :=
    scrfile.control("patternGeneratorWriteAddr")

  val patternGeneratorWriteData = (0 until dataWidthWords).map(i =>
      scrfile.control(s"patternGeneratorWriteData_$i")
      )

  patternGenerator.io.memory.bits.writeData(0) :=
    patternGeneratorWriteData.reduce({(x:UInt, y:UInt) => util.Cat(x,y) })

  decoupledHelper(
    patternGenerator.io.memory.valid,
    patternGenerator.io.memory.ready,
    scrfile.control("patternGeneratorWriteEnable") =/= 0.U,
    scrfile.status("patternGeneratorWriteFinished"))

  patternGenerator.io.control.bits.readyBypass :=
    scrfile.control("patternGeneratorReadyBypass")
  patternGenerator.io.control.bits.triggerMode :=
    scrfile.control("patternGeneratorTriggerMode")
  patternGenerator.io.control.bits.lastSample :=
    scrfile.control("patternGeneratorLastSample")
  patternGenerator.io.control.bits.continuous :=
    scrfile.control("patternGeneratorContinuous")
  patternGenerator.io.control.bits.arm :=
    scrfile.control("patternGeneratorArm")
  patternGenerator.io.control.bits.abort :=
    scrfile.control("patternGeneratorAbort")

  decoupledHelper(
    patternGenerator.io.control.valid,
    patternGenerator.io.control.ready,
    scrfile.control("patternGeneratorControlEnable") =/= 0.U,
    scrfile.status("patternGeneratorControlFinished"))

  val patternGeneratorSelects = (0 until blocks.length).map(i =>
      scrfile.control("patternGeneratorSelect") === i.U &&
      scrfile.control("patternGeneratorEnable") =/= 0.U
      )

  patternGenerator.io.signal.ready := true.B
}

trait HasLogicAnalyzerModule extends HasDspChainParameters with HasDecoupledSCR {
  def scrfile: SCRFile
  def dataWidthWords: Int
  def wordWidth: Int

  val logicAnalyzer    = Module(new LogicAnalyzer(
                                  dataWidthWords*wordWidth, 1,
                                  logicAnalyzerSamples,
                                  logicAnalyzerUseCombinationalTrigger)
                                )
  scrfile.status("logicAnalyzerState")      :=
    logicAnalyzer.io.status.state
  scrfile.status("logicAnalyzerNumSampled") :=
    logicAnalyzer.io.status.numSampled
  scrfile.status("logicAnalyzerOverflow")   :=
    logicAnalyzer.io.status.overflow

  logicAnalyzer.io.memory.reqAddr :=
    scrfile.control("logicAnalyzerWriteAddr")

  val logicAnalyzerWriteData = (0 until dataWidthWords).map(i =>
      scrfile.status(s"logicAnalyzerWriteData_$i")
      )

  logicAnalyzerWriteData.zipWithIndex.foreach { case(la, idx) =>
    val width = la.getWidth
    la := logicAnalyzer.io.memory.respData(0)(width * (idx + 1) - 1, width * idx)
  }

  logicAnalyzer.io.control.bits.validBypass :=
    scrfile.control("logicAnalyzerValidBypass")
  logicAnalyzer.io.control.bits.triggerMode :=
    scrfile.control("logicAnalyzerTriggerMode")
  logicAnalyzer.io.control.bits.numSamples :=
    scrfile.control("logicAnalyzerNumSamples")
  logicAnalyzer.io.control.bits.arm :=
    scrfile.control("logicAnalyzerArm")
  logicAnalyzer.io.control.bits.abort :=
    scrfile.control("logicAnalyzerAbort")

  decoupledHelper(
    logicAnalyzer.io.control.valid,
    logicAnalyzer.io.control.ready,
    scrfile.control("logicAnalyzerControlEnable") =/= 0.U,
    scrfile.status("logicAnalyzerControlFinished"))

  val logicAnalyzerSelects = (0 until blocks.length).map(i =>
      scrfile.control("logicAnalyzerSelect") === i.U &&
      scrfile.control("logicAnalyzerEnable") =/= 0.U
      )

  logicAnalyzer.io.signal.valid := false.B
  logicAnalyzer.io.signal.bits  := 0.U

}

class DspChainIO(firstBlock: DspBlockModule)(implicit val p: Parameters) extends Bundle {
  val control_axi = Flipped(new NastiIO())
  val data_axi    = Flipped(new NastiIO())

  val stream_in   = Flipped(firstBlock.io.in.cloneType)
}

trait HasSCRBuilder {
  def name: String
  val scrbuilder = new SCRBuilder(name + ":scr")
}

class DspChain(
  val ctrlBaseAddr: Long,
  val dataBaseAddr: Long,
  b: => Option[DspChainIO] = None,
  override_clock: Option[Clock]=None,
  override_reset: Option[Bool]=None)
  (implicit val p: Parameters) extends LazyModule with HasSCRBuilder
  with HasPatternGenerator with HasLogicAnalyzer {
  override def module: DspChainModule = new DspChainModule(this, b, override_clock, override_reset)(p)
}

trait HasSCRFile {
  def outer: DspChain
  implicit def p: Parameters
  val scrfile = outer.scrbuilder.generate(0)
}

class DspChainModule(
  val outer: DspChain,
  b: => Option[DspChainIO] = None,
  override_clock: Option[Clock]=None,
  override_reset: Option[Bool]=None)(val p: Parameters)
  extends LazyModuleImp(outer, override_clock, override_reset)
  with HasSCRFile with HasDspChainParameters
  with HasPatternGeneratorModule with HasLogicAnalyzerModule {

  val ctrlBaseAddr = outer.ctrlBaseAddr
  val dataBaseAddr = outer.dataBaseAddr
  val tlid = p(TLId)
  println(s"TLId is $tlid")
  val tlkey = p(TLKey(tlid))
  implicit val overrideParams = p.alterPartial({
    case TLKey(tlid) => tlkey.copy(
    //  dataBits = 4 * 64,
      overrideDataBitsPerBeat = Some(64)
      )})

  require(blocks.length > 0)
  var addr = 0
  val lazy_mods = blocks.map(b => {
    val modParams = overrideParams.alterPartial({
      case BaseAddr(_id) if _id == b._2 => addr
      case DspBlockId => b._2
    })
    val mod = LazyModule(b._1(modParams))
    addr += mod.size
    mod
  })
  val modules = lazy_mods.map(mod => Module(mod.module))
  val mod_ios = modules.map(_.io)
  val io = IO(b.getOrElse(new DspChainIO(modules.head)))

  val maxDataWidth = mod_ios.map(i =>
      math.max(i.in.bits.getWidth, i.out.bits.getWidth)
  ).reduce(math.max(_, _))

  val lastDataWidth = mod_ios.last.out.bits.getWidth

  for (i <- 0 until mod_ios.length - 1) {
    require(mod_ios(i+1).in.bits.getWidth == mod_ios(i).out.bits.getWidth,
      "Connecting modules with different width IOs")
    mod_ios(i + 1).in <> mod_ios(i).out
  }

  val oldSamConfig: SAMConfig = p(DefaultSAMKey)

  val lazySams = modules.map(mod => {
    val samWidth = mod.io.out.bits.getWidth
    val samName = mod.id + ":sam"
    val samParams = overrideParams.alterPartial({
      case SAMKey(_id) if _id == samName => oldSamConfig.copy()
      case DspBlockId => samName
      case DspBlockKey(_id) if _id == samName => DspBlockParameters(samWidth, samWidth)
      case BaseAddr(_id) if _id == samName => addr
    })
    val lazySam = LazyModule( new LazySAM()(samParams) )
    addr += lazySam.size
    lazySam
  })
  val sams = lazySams.map(ls => {
    val sam = Module(ls.module)
    sam
  })

  val scrbuildersize = outer.scrbuilder.controlNames.length + outer.scrbuilder.statusNames.length
  // addr += scrbuildersize

  val scrfile_tl2axi = Module(new TileLinkIONastiIOConverter())
  scrfile_tl2axi.io.tl <> scrfile.io.tl


  // connect input to first module
  when (patternGeneratorSelects(0)) {
    mod_ios.head.in.bits := patternGenerator.io.signal.bits
    mod_ios.head.in.valid := patternGenerator.io.signal.valid
  } .otherwise {
    mod_ios.head.in <> io.stream_in
  }

  for (i <- 1 until mod_ios.length) {
    when (patternGeneratorSelects(i)) {
      mod_ios(i).in.bits := patternGenerator.io.signal.bits
      mod_ios(i).in.valid := patternGenerator.io.signal.valid
    } .otherwise {
      mod_ios(i).in <> mod_ios(i-1).out
    }
  }

  for (i <- 0 until mod_ios.length) {
    when (logicAnalyzerSelects(i)) {
      logicAnalyzer.io.signal.valid := mod_ios(i).out.valid
      logicAnalyzer.io.signal.bits  := mod_ios(i).out.bits
    }
  }

  // connect output of each module to appropriate SAM
  modules.zip(sams).foreach {case (mod, sam) =>
    sam.io.in := mod.io.out
  }

  val control_axis = mod_ios.map(_.axi)
  // + 1 for PG and LA
  val ctrlOutPorts = control_axis.length + sams.length + 1
  val dataOutPorts = sams.length

  val ctrlAddrs = new AddrMap(lazy_mods.map(_.addrMapEntry
    //AddrMapEntry(s"${mod.id}", MemSize(mod.size, MemAttr(AddrMapProt.RWX)))
  ) ++ lazySams.zipWithIndex.map({ case(sam, idx) =>
    AddrMapEntry(s"sam${idx}", MemSize(BigInt(1 << 8), MemAttr(AddrMapProt.RWX)))
  }) ++ Seq(
    AddrMapEntry(s"chain", MemSize(BigInt(1 << 8), MemAttr(AddrMapProt.RWX)))
  )
  )

  val dataAddrs = sams.zipWithIndex.map({ case (sam, idx) =>
    AddrMapEntry(s"${sam}_${idx}_data", MemSize(sam.config.memDepth, MemAttr(AddrMapProt.RWX)))
  })

  val inPorts = 1
  val ctrlXbarParams = p.alterPartial({
    case TLKey("XBar") => p(TLKey("MCtoEdge")).copy(
      overrideDataBitsPerBeat = Some(64),
      nCachingClients = 0,
      nCachelessClients = ctrlOutPorts,
      maxClientXacts = 4,
      maxClientsPerPort = inPorts)
    case TLId => "XBar"
    case GlobalAddrMap => {
      val memSize = 0x1000L
      ctrlAddrs
    }
    case XBarQueueDepth => 2
    case ExtMemSize => 0x1000L
    case InPorts => inPorts
    case OutPorts => ctrlOutPorts
  })
  val dataXbarParams = p.alterPartial({
    case TLKey("XBar") => p(TLKey("MCtoEdge")).copy(
      overrideDataBitsPerBeat = Some(64),
      nCachingClients = 0,
      nCachelessClients = dataOutPorts,
      maxClientXacts = 4,
      maxClientsPerPort = inPorts)
    case TLId => "XBar"
    case GlobalAddrMap => {
      val memSize = 0x1000L
      new AddrMap(dataAddrs)
    }
    case XBarQueueDepth => 2
    case ExtMemSize => 0x1000L
    case InPorts => inPorts
    case OutPorts => dataOutPorts
  })

  DspChain._addrMaps += (s"$id:data" -> new AddrMap(dataAddrs))
  DspChain._addrMaps += (s"$id:ctrl" -> ctrlAddrs)
  println(s"In the address map list we have keys ${DspChain.addrMapIds()}")
  for (key <- DspChain.addrMapIds()) {
    println(s"AddrMap($key) =\n${DspChain.addrMap(key)}")
  }
  println(s"After instantiating everything, addr is $addr")
  println(s"We have ${control_axis.length} OutPorts on the XBar")
  println(s"We have ${ctrlXbarParams(GlobalAddrMap).numSlaves} ctrl slaves in the AddrMap")
  println(s"We have ${dataXbarParams(GlobalAddrMap).numSlaves} data slaves in the AddrMap")


  val ctrlXbar = Module(new NastiXBar(ctrlXbarParams))
  val dataXbar = Module(new NastiXBar(dataXbarParams))

  ctrlXbar.io.in(0) <> io.control_axi
  ctrlXbar.io.out.zip(control_axis).foreach{ case (xbar_axi, control_axi) => xbar_axi <> control_axi }
  ctrlXbar.io.out.last <> scrfile_tl2axi.io.nasti

  dataXbar.io.in(0) <> io.data_axi
  dataXbar.io.out.zip(sams).foreach{ case (xbar_axi, sam) => xbar_axi <> sam.io.axi }
}
