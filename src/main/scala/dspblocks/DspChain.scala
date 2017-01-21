// See LICENSE for license details

package dspblocks

import cde._
import chisel3._
import craft._
// import debuggers._
import _root_.junctions._
import diplomacy.LazyModule
import dspjunctions._
import sam._
import testchipip._
import uncore.converters._
import uncore.tilelink._
import junctions._
import rocketchip._

case class DspChainParameters (
  blocks: Seq[(Parameters => LazyDspBlock, String)],
  ctrlBaseAddr: Int,
  dataBaseAddr: Int
)

case object DspChainId extends Field[String]
case class DspChainKey(id: String) extends Field[DspChainParameters]

trait HasDspChainParameters {
  implicit val p: Parameters
  val dspChainExternal = p(DspChainKey(p(DspChainId)))
  val blocks           = dspChainExternal.blocks
  val ctrlBaseAddr     = dspChainExternal.ctrlBaseAddr
  val dataBaseAddr     = dspChainExternal.dataBaseAddr
}

class DspChainIO()(implicit val p: Parameters) extends Bundle {
  val firstBlockId = p(DspChainKey(p(DspChainId))).blocks.head._2

  val control_axi = Flipped(new NastiIO())
  val data_axi = Flipped(new NastiIO())
  val stream_in = Flipped(ValidWithSync(UInt( (p(GenKey(firstBlockId)).genIn.getWidth * p(GenKey(firstBlockId)).lanesIn).W )))
}

class DspChain(
  b: => Option[DspChainIO] = None,
  override_clock: Option[Clock]=None,
  override_reset: Option[Bool]=None)(implicit val p: Parameters)
  extends Module(override_clock, override_reset)
  with HasDspChainParameters {
  val io = IO(b.getOrElse(new DspChainIO))

  require(blocks.length > 0)
  var addr = 0
  val lazy_mods = blocks.map(b => {
    val modParams = p.alterPartial({
      case BaseAddr => addr
      case DspBlockId => b._2
    })
    val mod = LazyModule(b._1(modParams))
    addr += mod.size
    mod
  })
  val modules = lazy_mods.map(mod => Module(mod.module))
  val mod_ios = modules.map(_.io)

  val maxDataWidth = mod_ios.map(i =>
      math.max(i.in.bits.getWidth, i.out.bits.getWidth)
  ).reduce(math.max(_, _))

  val lastDataWidth = mod_ios.last.out.bits.getWidth

  for (i <- 0 until mod_ios.length - 1) {
    require(mod_ios(i+1).in.bits.getWidth == mod_ios(i).out.bits.getWidth,
      "Connecting modules with different width IOs")
    mod_ios(i + 1).in <> mod_ios(i).out
  }

  val oldSamConfig: SAMConfig = p(SAMKey)

  val lazySams = modules.map(mod => {
    val samWidth = mod.io.out.bits.getWidth
    val samParams = p.alterPartial({
      case SAMKey => oldSamConfig.copy(baseAddr = addr)
      case DspBlockId => "sam"
      case DspBlockKey("sam") => DspBlockParameters(samWidth, samWidth)
      case BaseAddr => addr
    })
    val lazySam = LazyModule( new LazySAM()(samParams) )
    addr += lazySam.size
    lazySam
  })
  val sams = lazySams.map(ls => {
    val sam = Module(ls.module)
    sam
  })

  // connect input to first module
  mod_ios.head.in := io.stream_in

  // connect output of each module to appropriate SAM
  modules.zip(sams).foreach {case (mod, sam) =>
    sam.io.in := mod.io.out
  }

  val control_axis = mod_ios.map(_.axi)
  val ctrlOutPorts = control_axis.length + sams.length
  val dataOutPorts = sams.length

  val addrs = lazy_mods.map(mod =>
    AddrMapEntry(s"${mod.name}", MemSize(mod.size, MemAttr(AddrMapProt.RWX)))
  ) ++ lazySams.zipWithIndex.map({ case(sam, idx) =>
    AddrMapEntry(s"sam${idx}", MemSize(sam.size, MemAttr(AddrMapProt.RWX)))
  })

  val inPorts = 1
  val ctrlXbarParams = p.alterPartial({
    case TLKey("XBar") => p(TLKey("MCtoEdge")).copy(
      nCachingClients = 0,
      nCachelessClients = ctrlOutPorts,
      maxClientXacts = 4,
      maxClientsPerPort = inPorts)
    case TLId => "XBar"
    case GlobalAddrMap => {
      val memSize = 0x1000L
      new AddrMap(addrs)
    }
    case XBarQueueDepth => 2
    case ExtMemSize => 0x1000L
    case InPorts => inPorts
    case OutPorts => ctrlOutPorts
  })
  val dataXbarParams = p.alterPartial({
    case TLKey("XBar") => p(TLKey("MCtoEdge")).copy(
      nCachingClients = 0,
      nCachelessClients = dataOutPorts,
      maxClientXacts = 4,
      maxClientsPerPort = inPorts)
    case TLId => "XBar"
    case GlobalAddrMap => {
      val memSize = 0x1000L
      new AddrMap(addrs)
    }
    case XBarQueueDepth => 2
    case ExtMemSize => 0x1000L
    case InPorts => inPorts
    case OutPorts => dataOutPorts
  })

  println(s"After instantiating everything, addr is $addr")
  println(s"We have ${control_axis.length} OutPorts on the XBar")
  println(s"We have ${ctrlXbarParams(GlobalAddrMap).numSlaves} ctrl slaves in the AddrMap")
  println(s"We have ${dataXbarParams(GlobalAddrMap).numSlaves} data slaves in the AddrMap")


  val ctrlXbar = Module(new CraftXBar(ctrlXbarParams))
  val dataXbar = Module(new CraftXBar(ctrlXbarParams))

  ctrlXbar.io.in(0) <> io.control_axi
  ctrlXbar.io.out.zip(control_axis).foreach{ case (xbar_axi, control_axi) => xbar_axi <> control_axi }

  dataXbar.io.in(0) <> io.data_axi
  dataXbar.io.out.zip(sams).foreach{ case (xbar_axi, sam) => xbar_axi <> sam.io.axi }
}
