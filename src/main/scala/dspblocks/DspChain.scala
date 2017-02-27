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

  scrbuilder.addControl("patternGeneratorEnable", 0.U)
  scrbuilder.addControl("patternGeneratorSelect", 0.U)
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

  scrbuilder.addControl("logicAnalyzerEnable", 0.U)
  scrbuilder.addControl("logicAnalyzerSelect", 0.U)
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

  lazy val patternGenerator = {
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


    patternGenerator.io.signal.ready := true.B

    patternGenerator
  }

  lazy val patternGeneratorSelects = (0 until blocks.length).map(i =>
      scrfile.control("patternGeneratorSelect") === i.U &&
      scrfile.control("patternGeneratorEnable") =/= 0.U
      )
}

trait HasLogicAnalyzerModule extends HasDspChainParameters with HasDecoupledSCR {
  def scrfile: SCRFile
  def dataWidthWords: Int
  def wordWidth: Int

  lazy val logicAnalyzer = {
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


    logicAnalyzer.io.signal.valid := false.B
    logicAnalyzer.io.signal.bits  := 0.U

    logicAnalyzer
  }
  lazy val logicAnalyzerSelects = (0 until blocks.length).map(i =>
        scrfile.control("logicAnalyzerSelect") === i.U &&
        scrfile.control("logicAnalyzerEnable") =/= 0.U
        )
}

class DspChainIO(firstBlock: DspBlockModule)(implicit val p: Parameters) extends Bundle {
  val control_axi = Flipped(new NastiIO())
  val data_axi    = Flipped(new NastiIO())

  val stream_in   = Flipped(firstBlock.io.in.cloneType)
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

class DspChainModule(
  val outer: DspChain,
  b: => Option[DspChainIO] = None,
  override_clock: Option[Clock]=None,
  override_reset: Option[Bool]=None)(implicit val p: Parameters)
  extends LazyModuleImp(outer, override_clock, override_reset)
    with HasDspChainParameters
    with HasPatternGeneratorModule with HasLogicAnalyzerModule {

  val ctrlBaseAddr = outer.ctrlBaseAddr
  val dataBaseAddr = outer.dataBaseAddr

  val tlid = p(TLId)
  println(s"TLId is $tlid")
  val tlkey = p(TLKey(tlid))
  require(tlkey.dataBitsPerBeat == 64,
    s"SCR File in DspChain requires 64-bit data bits per beat, got ${tlkey.dataBitsPerBeat}")
  /*implicit val overrideParams = p.alterPartial({
    case TLKey(tlid) => tlkey.copy(
    //  dataBits = 4 * 64,
      overrideDataBitsPerBeat = Some(64)
  )})*/

  require(blocks.length > 0)

  val lazyMods = blocks.map(b => {
    val modParams = p.alterPartial({
      case DspBlockId => b._2
    })
    val mod = LazyModule(b._1(modParams))
    mod
  })
  val oldSamConfig: SAMConfig = p(SAMKey)
  val lazySams = lazyMods.map(mod => {
    val samWidth = 64 // todo don't hardcode...
    val samName = mod.id + ":sam"
    val samParams = p.alterPartial({
      case SAMKey(_id) if _id == samName => oldSamConfig
      case DspBlockId => samName
      case DspBlockKey(_id) if _id == samName => DspBlockParameters(samWidth, samWidth)
      case IPXactParameters(_id) if _id == samName => {
        val parameterMap = scala.collection.mutable.HashMap[String, String]()

        // Conjure up some IPXACT synthsized parameters.
        parameterMap ++= List(("InputTotalBits", samWidth.toString))

        parameterMap
      }
    })
    val lazySam = LazyModule( new SAMWrapper()(samParams) )
    lazySam
  })

  val ctrlAddrs = new AddrMap(
    lazyMods.map(_.addrMapEntry) ++
    lazySams.map(_.addrMapEntry) ++
    Seq(
      AddrMapEntry(s"chain", MemSize(BigInt(1 << 8), MemAttr(AddrMapProt.RWX)))
    ),
  start=ctrlBaseAddr)

  (lazyMods ++ lazySams).zip(ctrlAddrs.entries).foreach{ case (mod, addr) =>
    mod.setBaseAddr(addr.region.start)
  }

  val scrfile = outer.scrbuilder.generate(ctrlAddrs.entries.last.region.start)

  val modules = lazyMods.map(mod => Module(mod.module))
  modules.map(m => 
    IPXactComponents._ipxactComponents += DspIPXact.makeDspBlockComponent(m.baseAddr)(m.p)
    )
  val mod_ios = modules.map(_.io)
  val io = IO(b.getOrElse(new DspChainIO(modules.head)))

  // make sure the pattern generator and logic analyzer are instantitated
  patternGenerator
  logicAnalyzer


  val maxDataWidth = mod_ios.map(i =>
      math.max(i.in.bits.getWidth, i.out.bits.getWidth)
  ).reduce(math.max(_, _))

  val lastDataWidth = mod_ios.last.out.bits.getWidth

  for (i <- 0 until mod_ios.length - 1) {
    require(mod_ios(i+1).in.bits.getWidth == mod_ios(i).out.bits.getWidth,
      "Connecting modules with different width IOs")
    mod_ios(i + 1).in <> mod_ios(i).out
  }

  val dataAddrs = new AddrMap(
    lazySams.zipWithIndex.map({ case (sam, idx) =>
      AddrMapEntry(s"${sam.name}_${idx}_data", MemSize(sam.config.memDepth * 8 * 16, MemAttr(AddrMapProt.RWX)))
    }), start=dataBaseAddr)
  lazySams.zip(dataAddrs.entries).foreach { case(sam, addr) =>
    sam.setDataBaseAddr(addr.region.start)
  }
  val sams = lazySams.map(ls => {
    val sam = Module(ls.module)
    sam
  })
  sams.map(s =>
    IPXactComponents._ipxactComponents += DspIPXact.makeSAMComponent(s.baseAddr, s.dataBaseAddr)(s.p)
    )

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

  val control_axis = (modules ++ sams).map(_.io.axi) :+ scrfile_tl2axi.io.nasti

  val ctrlOutPorts = control_axis.length
  val dataOutPorts = sams.length

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
    case DspBlockId => s"$id:ctrl"
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
      dataAddrs
    }
    case XBarQueueDepth => 2
    case ExtMemSize => 0x1000L
    case InPorts => inPorts
    case OutPorts => dataOutPorts
    case DspBlockId => s"$id:data"
  })

  DspChain._addrMaps += (s"$id:data" -> dataAddrs)
  DspChain._addrMaps += (s"$id:ctrl" -> ctrlAddrs)
  println(s"In the address map list we have keys ${DspChain.addrMapIds()}")
  for (key <- DspChain.addrMapIds()) {
    println(s"AddrMap($key) =\n${DspChain.addrMap(key).entries.foreach(x => println(x.toString))}")
  }
  println(s"We have ${control_axis.length} OutPorts on the XBar")
  println(s"We have ${ctrlXbarParams(GlobalAddrMap).numSlaves} ctrl slaves in the AddrMap")
  println(s"We have ${dataXbarParams(GlobalAddrMap).numSlaves} data slaves in the AddrMap")


  val ctrlXbar = Module(new NastiXBar(ctrlXbarParams))
  IPXactComponents._ipxactComponents += DspIPXact.makeXbarComponent(ctrlXbarParams)
  val dataXbar = Module(new NastiXBar(dataXbarParams))
  IPXactComponents._ipxactComponents += DspIPXact.makeXbarComponent(dataXbarParams)

  ctrlXbar.io.in(0) <> io.control_axi
  ctrlXbar.io.out.zip(control_axis).foreach{ case (xbar_axi, control_axi) => xbar_axi <> control_axi }

  dataXbar.io.in(0) <> io.data_axi
  dataXbar.io.out.zip(sams).foreach{ case (xbar_axi, sam) => xbar_axi <> sam.io.asInstanceOf[SAMWrapperIO].axi_out }
}
