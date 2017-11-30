package dspblocks

// import util.GeneratorApp
import org.accellera.spirit.v1685_2009.{File => SpiritFile, Parameters => SpiritParameters, _}
import freechips.rocketchip.diplomacy.LazyModule

import ipxact._
import freechips.rocketchip.config._

object IPXactComponents {
  //private[dspblocks] val _ipxactComponents: scala.collection.mutable.ArrayBuffer[ComponentType] = 
  val _ipxactComponents: scala.collection.mutable.ArrayBuffer[ComponentType] = 
    scala.collection.mutable.ArrayBuffer.empty[ComponentType]
  def ipxactComponents(): Seq[ComponentType] = _ipxactComponents
}

trait HasIPXactParameters {
  def ipxactParameters: scala.collection.Map[String, String]
}

trait HasDspIPXact extends HasIPXact {

  //////////////////////////////////////////////
  //////////// BUS INTERFACES //////////////////
  //////////////////////////////////////////////

  def makeDspBlockInterfaces(block: AXI4DspBlock): BusInterfaces = {
    val mmref = s"${block.name}_mm"
    def getStreamInPrefix(idx: Int): String = {
      /*if (block.streamNode.in.length > 1) {
        s"auto_stream_in${idx}"
      } else {
        "auto_stream_in"
      }*/
      block.streamNode.in(idx)._1.instanceName
    }
    def getStreamOutPrefix(idx: Int): String = {
      /*if (block.streamNode.out.length > 1) {
        s"auto_stream_out${idx}"
      } else {
        "auto_stream_out"
      }*/
      block.streamNode.out(idx)._1.instanceName
    }

    val streamInInterfaces = block.streamNode.in.zipWithIndex.map { case ((bundle , _), idx) =>
      makeAXI4StreamInterface(getStreamInPrefix(idx), bundle, "data_in", direction = false)
    }
    val streamOutInterfaces = block.streamNode.out.zipWithIndex.map { case ((bundle , _), idx) =>
      makeAXI4StreamInterface(getStreamOutPrefix(idx), bundle, "data_in", direction = true)
    }
    val axiInterfaces = block.mem.map (mem => makeMemInputInterfaces(mmref, "axi4_slave", mem)).getOrElse(Seq())

    val busInterfaces = new BusInterfaces
    busInterfaces.getBusInterface.addAll(toCollection(streamInInterfaces ++ streamOutInterfaces ++ axiInterfaces))
    busInterfaces
  }

  /*def makeSAMInterfaces(ctrl_mmref: String, data_mmref: String): BusInterfaces = {
    val streamInInterface = makeAXI4StreamInterface("io_in", "data_in", direction = false)
    val ctrlAXIInterface = makeAXI4Interface(ctrl_mmref, "io_axi", "axi4_slave", direction = false)
    val dataAXIInterface = makeAXI4Interface(data_mmref, "io_axi_out", "axi4_data_slave", direction = false)

    val busInterfaces = new BusInterfaces
    busInterfaces.getBusInterface.addAll(toCollection(Seq(streamInInterface, ctrlAXIInterface, dataAXIInterface)))
    busInterfaces
  }*/

  def makeMemInputInterfaces(mmref: String, asref: String, mem: AXI4DspBlock.AXI4Node): Seq[BusInterfaceType] = {
    def getMemInPrefix(idx: Int): String = {
      /*if (mem.in.length > 1) {
        s"auto_mem_in${idx}"
      } else {
        "auto_mem_in"
      }*/
      mem.in(idx)._1.instanceName
    }
    mem.in.zipWithIndex.map { case ((bundle, _), idx) =>
      makeAXI4Interface(mmref, getMemInPrefix(idx), bundle, asref, direction = false)
    }
  }
  def makeMemOutputInterfaces(mmref: String, asref: String, mem: AXI4DspBlock.AXI4Node): Seq[BusInterfaceType] = {
    def getMemOutPrefix(idx: Int): String = {
      /*if (mem.out.length > 1) {
        s"auto_mem_out${idx}"
      } else {
        "auto_mem_out"
      }*/
      mem.out(idx)._1.instanceName
    }

    mem.out.zipWithIndex.map { case ((bundle, _), idx) =>
      makeAXI4Interface(mmref, getMemOutPrefix(idx), bundle, asref, direction = true)
    }
  }

  def makeMemInterfaces(mmref: String, asref: String, mem: AXI4DspBlock.AXI4Node): Seq[BusInterfaceType] = {
    makeMemInputInterfaces(mmref, asref, mem) ++ makeMemOutputInterfaces(mmref, asref, mem)
  }

  def makeXbarInterfaces(mmref: String, asref: String, mem: AXI4DspBlock.AXI4Node): BusInterfaces = {
    val busInterfaces = new BusInterfaces
    busInterfaces.getBusInterface.addAll(toCollection(makeMemInterfaces(mmref, asref, mem)))
    busInterfaces
  }

  /*def makeSCRInterfaces(mmref: String): BusInterfaces = {
    val ctrlAXIInterface = makeAXI4Interface(mmref, "io_nasti", "axi4_slave", direction = false)

    val busInterfaces = new BusInterfaces
    busInterfaces.getBusInterface.add(ctrlAXIInterface)
    busInterfaces
  }*/


  //////////////////////////////////////////////
  //////////// Memory Maps /////////////////////
  //////////////////////////////////////////////

  def makeDspBlockMemoryMaps(block: LazyModule with AXI4HasCSR): MemoryMaps = {
    val mmref = s"${block.name}_mm"
    val baseAddress = block.csrs.base
    val registers = block.csrMap
    val addrBlock = makeAddressBlock("CSRs", baseAddress, block)
    val memoryMaps = new MemoryMaps
    memoryMaps.getMemoryMap.add(makeMemoryMap(mmref, addrBlocks=Seq(addrBlock)))
    memoryMaps
  }

  // TODO: make the data map correct
  /*def makeSAMMemoryMaps(ctrl_mmref: String, data_mmref: String, ctrl_baseAddress: BigInt, data_baseAddress: BigInt, registers: mutable.HashMap[String, BigInt], width: Int): MemoryMaps = {
    val ctrl_addrBlock = makeAddressBlock("SCRFile", ctrl_baseAddress, registers, 64)
    val data_addrBlock = makeAddressBlock("SAMData", data_baseAddress, mutable.HashMap(("SAM", data_baseAddress)), width)
    val memoryMaps = new MemoryMaps
    memoryMaps.getMemoryMap.addAll(toCollection(Seq(makeMemoryMap(ctrl_mmref, addrBlocks=Seq(ctrl_addrBlock)), makeMemoryMap(data_mmref, addrBlocks=Seq(data_addrBlock)))))
    memoryMaps
  }*/

  // def makeXbarMemoryMaps(ref: String, inputs: Int, addrMap: AddrMap): MemoryMaps = {
  //   val signalMaps = addrMap.flatten.zipWithIndex.map { case (entry, i) =>
  //     (s"io_out_${i}", entry.region.start)
  //   }
  //   val memoryMaps = new MemoryMaps
  //   memoryMaps.getMemoryMap().addAll(toCollection(
  //     (0 until inputs).map(i => makeMemoryMap(s"${ref}_${i}", subspaceRefs=signalMaps.map{ case(name, baseAddr) => makeSubspaceRef(s"subspacemap_${ref}_${name}_${i}", baseAddr, name)}))
  //   ))
  //   memoryMaps
  // }

  // def makeSCRMemoryMaps(mmref: String, baseAddress: BigInt, registers: HashMap[String, BigInt]): MemoryMaps = {
  //   val addrBlock = makeAddressBlock("SCRFile", baseAddress, registers, 64)
  //   val memoryMaps = new MemoryMaps
  //   memoryMaps.getMemoryMap().add(makeMemoryMap(mmref, addrBlocks=Seq(addrBlock)))
  //   memoryMaps
  // }

  //////////////////////////////////////////////
  //////////// Address Spaces //////////////////
  //////////////////////////////////////////////

  def makeDspBlockAddressSpaces: AddressSpaces = {
    new AddressSpaces
  }

  def makeSAMAddressSpaces: AddressSpaces = {
    new AddressSpaces
  }

  // def makeXbarAddressSpaces(ref: String, addrMap: AddrMap): AddressSpaces = {
  //   val addressSpaces = new AddressSpaces
  //   addressSpaces.getAddressSpace.addAll(toCollection(
  //     addrMap.flatten.zipWithIndex.map { case (entry, i) =>
  //       makeAddressSpace(s"${ref}_${i}", entry.region.size)
  //     }
  //   ))
  //   addressSpaces
  // }

  def makeSCRAddressSpaces: AddressSpaces = {
    new AddressSpaces
  }


  //////////////////////////////////////////////
  //////////// Model ///////////////////////////
  //////////////////////////////////////////////

  // assumes DSP block, stream in and out and AXI4 control
  def makeDspBlockPorts(module: AXI4DspBlock): ModelType.Ports = {
    val streamInPorts = module.streamNode.in.zipWithIndex.flatMap { case ((in, _), idx) =>
      makeAXI4StreamPorts(in.instanceName, in)
    }
    val streamOutPorts = module.streamNode.out.zipWithIndex.flatMap { case ((out, _), idx) =>
      makeAXI4StreamPorts(out.instanceName, out)
    }
    val axiPorts = module.mem.map( axi => axi.in.zipWithIndex.flatMap { case ((in, _), idx) =>
        makeAXI4Ports(in.instanceName, in)
    }).getOrElse(Seq())
    val globalPorts = makeClockAndResetPorts

    val ports = new ModelType.Ports
    ports.getPort().addAll(toCollection(globalPorts ++ streamInPorts ++ streamOutPorts ++ axiPorts))
    ports
  }

  // assumes SAM block, stream in and AXI4 control and data out
  /*def makeSAMPorts(bits_in: Int)(implicit p: Parameters): ModelType.Ports = {
    // TODO: do we need to separate control from data AXI interface configs here?
    // val config = new NastiConfig
    val streamInPorts = makeAXI4StreamPorts(s"io_in", direction = false, bits_in)
    // val ctrlAXIPorts = makeAXI4Ports(s"io_axi", false, config)
    // val dataAXIPorts = makeAXI4Ports(s"io_axi_out", false, config)
    val globalPorts = makeClockAndResetPorts

    val ports = new ModelType.Ports
    // ports.getPort().addAll(toCollection(globalPorts ++ streamInPorts ++ ctrlAXIPorts ++ dataAXIPorts))
    ports
  }*/

  def makeXbarPorts(inputs: Int, outputs: Int)(implicit p: Parameters): ModelType.Ports = {
    // val config = new NastiConfig
    // val inPorts = (0 until inputs).map(i => makeAXI4Ports(s"io_in_${i}", false, config))
    // val outPorts = (0 until outputs).map(i => makeAXI4Ports(s"io_out_${i}", true, config))
    val globalPorts = makeClockAndResetPorts

    val ports = new ModelType.Ports
    // ports.getPort().addAll(toCollection(globalPorts ++ (inPorts ++ outPorts).flatten))
    ports
  }

  def makeSCRPorts()(implicit p: Parameters): ModelType.Ports = {
    // val config = new NastiConfig
    // val AXIPorts = makeAXI4Ports(s"io_axi", false, config)
    val globalPorts = makeClockAndResetPorts

    val ports = new ModelType.Ports
    // ports.getPort().addAll(toCollection(globalPorts ++ AXIPorts))
    ports
  }

  //////////////////////////////////////////////
  //////////// Component ///////////////////////
  //////////////////////////////////////////////

  def makeDspBlockComponent(module: AXI4DspBlock)(implicit p: Parameters): ComponentType = {
    val name = "BlindModule"//module.name
    val mmref = s"${name}_mm"
    // val id = p(DspBlockId)
    /*val gk = try {
      None //Some(p(GenKey(p(DspBlockId))))
    } catch  {
      case e: MatchError => None
    }*/
    /*val dbk = try {
      None // Some(p(DspBlockKey(p(DspBlockId))))
    } catch {
      case e: MatchError => None
    }*/
    // val (bits_in, bits_out) = (gk, dbk) match {
    //   case (Some(g), None) => (g.genIn.getWidth * g.lanesIn, g.genOut.getWidth * g.lanesOut)
    //   case (None, Some(d)) => (d.inputWidth, d.outputWidth)
    //   case _ => throw dsptools.DspException("Input and output widths could not be found in the Parameters object!")
    // }
    //val baseAddress = _baseAddress
    // val registers = testchipip.SCRAddressMap(p(DspBlockId)).getOrElse(new HashMap[String, BigInt])
    //val registers = new mutable.HashMap[String, BigInt]
    val busInterfaces = makeDspBlockInterfaces(module)
    val addressSpaces = makeDspBlockAddressSpaces
    val memoryMaps = module match {
      case m: AXI4DspBlock with AXI4HasCSR => makeDspBlockMemoryMaps(m)
      case _ => new MemoryMaps // no CSR, empty memory map
    }
    val model = makeModel(makeDspBlockPorts(module))
    val parameterMap = module match {
        case m: HasIPXactParameters => m.ipxactParameters
        case _ => Map[String, String]()
      }
    val parameters = makeParameters(parameterMap)
    makeComponent(name, busInterfaces, addressSpaces, memoryMaps, model, parameters)
  }

  /*def makeSAMComponent(_ctrl_baseAddress: BigInt, _data_baseAddress: BigInt, memDepth: Int, uuid: Int, name: String)(implicit p: Parameters): ComponentType = {
    // val id = p(DspBlockId)
    val ctrl_mmref = s"${name}_ctrl_mm"
    val data_mmref = s"${name}_data_mm"
    // val dbk = p(DspBlockKey(p(DspBlockId)))
    val bits_in = 10 // dbk.inputWidth
    val ctrl_baseAddress = _ctrl_baseAddress
    val data_baseAddress = _data_baseAddress
    // val registers = testchipip.SCRAddressMap(p(DspBlockId)).getOrElse(new HashMap[String, BigInt])
    val registers = new mutable.HashMap[String, BigInt]
    val busInterfaces = makeSAMInterfaces(ctrl_mmref, data_mmref) 
    val addressSpaces = makeSAMAddressSpaces
    val memoryMaps = makeSAMMemoryMaps(ctrl_mmref, data_mmref, ctrl_baseAddress, data_baseAddress, registers, memDepth)
    val model = makeModel(makeSAMPorts(bits_in))
    val parameterMap = mutable.HashMap[String, String]()
    // parameterMap ++= p(IPXactParameters(p(DspBlockId))) 
    // parameterMap ++= List(("uuid", uuid.toString))
    val parameters = makeParameters(parameterMap)
    makeComponent(name, busInterfaces, addressSpaces, memoryMaps, model, parameters)
  }*/

  def makeXbarComponent(implicit p: Parameters, name: String): ComponentType = {
    // val id = p(DspBlockId)
    val mmref = s"${name}_mm"
    val asref = s"${name}_as"
    // val addrMap = p(GlobalAddrMap)
    // val inputs = p(InPorts)
    // val outputs = p(OutPorts)
    // val usePortQueues = p(XBarUsePortQueues)
    // val busInterfaces = makeXbarInterfaces(inputs, outputs, mmref, asref) 
    // val addressSpaces = makeXbarAddressSpaces(asref, addrMap)
    // val memoryMaps = makeXbarMemoryMaps(mmref, inputs, addrMap)
    // val model = makeModel(makeXbarPorts(inputs, outputs))
    // val parameters = makeParameters(HashMap[String, String](
    //   "usePortQueues" -> usePortQueues.toString))
    // makeComponent(name, busInterfaces, addressSpaces, memoryMaps, model, parameters)
    throw new Exception("Argh")
  }

  // def makeSCRComponent(_baseAddress: BigInt, id: String, name: String)(implicit p: Parameters): ComponentType = {
  //   val mmref = s"${name}_mm"
  //   val baseAddress = _baseAddress
  //   val registers = testchipip.SCRAddressMap(id).getOrElse(new HashMap[String, BigInt])
  //   val busInterfaces = makeSCRInterfaces(mmref) 
  //   val addressSpaces = makeSCRAddressSpaces
  //   val memoryMaps = makeSCRMemoryMaps(mmref, baseAddress, registers)
  //   val model = makeModel(makeSCRPorts)
  //   val parameters = makeParameters(HashMap[String, String]())
  //   makeComponent(name, busInterfaces, addressSpaces, memoryMaps, model, parameters)
  // }
}

object DspIPXact extends HasDspIPXact
