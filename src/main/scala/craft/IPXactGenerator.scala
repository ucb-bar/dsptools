package craft

import util.GeneratorApp
import org.accellera.spirit.v1685_2009.{File => SpiritFile, Parameters => SpiritParameters, _}
import javax.xml.bind.{JAXBContext, Marshaller}
import java.io.{File, FileOutputStream}
import scala.collection.JavaConverters
import java.util.Collection
import java.math.BigInteger
import rocketchip._
import junctions._
import cde._
import dspjunctions._
import dspblocks._
import dsptools._

class NastiConfig(implicit val p: Parameters) extends HasNastiParameters {}

trait IPXactGeneratorApp extends GeneratorApp {

  def toCollection[T](seq: Seq[T]): Collection[T] =
    JavaConverters.asJavaCollectionConverter(seq).asJavaCollection

  def makePortMap(logicalPortName: String, physicalPortName: String): BusInterfaceType.PortMaps.PortMap = {
    val logicalPort = new BusInterfaceType.PortMaps.PortMap.LogicalPort
    logicalPort.setName(logicalPortName)

    val physicalPort = new BusInterfaceType.PortMaps.PortMap.PhysicalPort
    physicalPort.setName(physicalPortName)

    val portmap = new BusInterfaceType.PortMaps.PortMap
    portmap.setLogicalPort(logicalPort)
    portmap.setPhysicalPort(physicalPort)
    portmap
  }

  def makePortMaps(mappings: Seq[(String, String)]): BusInterfaceType.PortMaps = {
    val portmaps = new BusInterfaceType.PortMaps
    portmaps.getPortMap().addAll(toCollection(
      mappings.sorted.map { case (log, phys) => makePortMap(log, phys) }))
    portmaps
  }

  // Assumes you use ValidWithSync Chisel bundle for AXI4-Stream
  def makeAXI4StreamPortMaps(prefix: String): BusInterfaceType.PortMaps = {
    makePortMaps(Seq(
      "ACLK"     -> "clock",
      "ARESETn"  -> "reset",
      "TVALID"   -> s"${prefix}_valid",
      "TLAST"    -> s"${prefix}_sync",
      "TDATA"    -> s"${prefix}_bits"))
  }

  // Assumes you use NastiIO Chisel bundle for AXI4
  def makeAXI4PortMaps(prefix: String): BusInterfaceType.PortMaps = {
    makePortMaps(Seq(
      "ACLK"     -> "clock",
      "ARESETn"  -> "reset",
      "ARVALID"  -> s"${prefix}_ar_valid",
      "ARREADY"  -> s"${prefix}_ar_ready",
      "ARID"     -> s"${prefix}_ar_bits_id",
      "ARADDR"   -> s"${prefix}_ar_bits_addr",
      "ARSIZE"   -> s"${prefix}_ar_bits_size",
      "ARLEN"    -> s"${prefix}_ar_bits_len",
      "ARBURST"  -> s"${prefix}_ar_bits_burst",
      "ARPROT"   -> s"${prefix}_ar_bits_prot",
      "ARLOCK"   -> s"${prefix}_ar_bits_lock",
      "ARQOS"    -> s"${prefix}_ar_bits_qos",
      "ARREGION" -> s"${prefix}_ar_bits_region",
      "ARCACHE"  -> s"${prefix}_ar_bits_cache",
      "ARUSER"   -> s"${prefix}_ar_bits_user",
      "AWVALID"  -> s"${prefix}_aw_valid",
      "AWREADY"  -> s"${prefix}_aw_ready",
      "AWID"     -> s"${prefix}_aw_bits_id",
      "AWADDR"   -> s"${prefix}_aw_bits_addr",
      "AWSIZE"   -> s"${prefix}_aw_bits_size",
      "AWLEN"    -> s"${prefix}_aw_bits_len",
      "AWBURST"  -> s"${prefix}_aw_bits_burst",
      "AWPROT"   -> s"${prefix}_aw_bits_prot",
      "AWLOCK"   -> s"${prefix}_aw_bits_lock",
      "AWQOS"    -> s"${prefix}_aw_bits_qos",
      "AWREGION" -> s"${prefix}_aw_bits_region",
      "AWCACHE"  -> s"${prefix}_aw_bits_cache",
      "AWUSER"   -> s"${prefix}_aw_bits_user",
      "WVALID"   -> s"${prefix}_w_valid",
      "WREADY"   -> s"${prefix}_w_ready",
      "WDATA"    -> s"${prefix}_w_bits_data",
      "WSTRB"    -> s"${prefix}_w_bits_strb",
      "WLAST"    -> s"${prefix}_w_bits_last",
      "WUSER"    -> s"${prefix}_w_bits_user",
      "RVALID"   -> s"${prefix}_r_valid",
      "RREADY"   -> s"${prefix}_r_ready",
      "RID"      -> s"${prefix}_r_bits_id",
      "RRESP"    -> s"${prefix}_r_bits_resp",
      "RDATA"    -> s"${prefix}_r_bits_data",
      "RLAST"    -> s"${prefix}_r_bits_last",
      "RUSER"    -> s"${prefix}_r_bits_user",
      "BVALID"   -> s"${prefix}_b_valid",
      "BREADY"   -> s"${prefix}_b_ready",
      "BID"      -> s"${prefix}_b_bits_id",
      "BRESP"    -> s"${prefix}_b_bits_resp",
      "BUSER"    -> s"${prefix}_b_bits_user"))
  }

  def makePort(name: String, direction: Boolean, width: Int): PortType = {
    val port = new PortType
    val wire = new PortWireType
    
    wire.setDirection(
      if (direction) ComponentPortDirectionType.OUT
      else ComponentPortDirectionType.IN)
    if (width > 1) {
      val vector = new Vector
      val left = new Vector.Left
      val right = new Vector.Right
      left.setValue(BigInteger.valueOf(width - 1))
      right.setValue(BigInteger.valueOf(0))
      vector.setLeft(left)
      vector.setRight(right)
      wire.setVector(vector)
    }

    port.setWire(wire)
    port.setName(name)
    port
  }

  def makeAXI4StreamPorts(prefix: String, direction: Boolean, bits: Int): Seq[PortType] = {
    val ports = Seq(
      ("valid", direction, 1),
      ("sync", direction, 1),
      ("bits", direction, bits))

    ports.sorted.map { case (name, portdir, width) =>
      makePort(s"${prefix}_${name}", portdir, width) }
  }

  def makeAXI4Ports(prefix: String, direction: Boolean, config: HasNastiParameters): Seq[PortType] = {
    val ports = Seq(
      ("ar_valid", direction, 1),
      ("ar_ready", !direction, 1),
      ("ar_bits_id", direction, config.nastiXIdBits),
      ("ar_bits_addr", direction, config.nastiXAddrBits),
      ("ar_bits_size", direction, config.nastiXSizeBits),
      ("ar_bits_len", direction, config.nastiXLenBits),
      ("ar_bits_burst", direction, config.nastiXBurstBits),
      ("ar_bits_lock", direction, 1),
      ("ar_bits_cache", direction, config.nastiXCacheBits),
      ("ar_bits_prot", direction, config.nastiXProtBits),
      ("ar_bits_qos", direction, config.nastiXQosBits),
      ("ar_bits_region", direction, config.nastiXRegionBits),
      ("ar_bits_user", direction, config.nastiXUserBits),
      ("aw_valid", direction, 1),
      ("aw_ready", !direction, 1),
      ("aw_bits_id", direction, config.nastiXIdBits),
      ("aw_bits_addr", direction, config.nastiXAddrBits),
      ("aw_bits_size", direction, config.nastiXSizeBits),
      ("aw_bits_len", direction, config.nastiXLenBits),
      ("aw_bits_burst", direction, config.nastiXBurstBits),
      ("aw_bits_lock", direction, 1),
      ("aw_bits_cache", direction, config.nastiXCacheBits),
      ("aw_bits_prot", direction, config.nastiXProtBits),
      ("aw_bits_qos", direction, config.nastiXQosBits),
      ("aw_bits_region", direction, config.nastiXRegionBits),
      ("aw_bits_user", direction, config.nastiXUserBits),
      ("w_valid", direction, 1),
      ("w_ready", !direction, 1),
      ("w_bits_data", direction, config.nastiXDataBits),
      ("w_bits_strb", direction, config.nastiWStrobeBits),
      ("w_bits_last", direction, 1),
      ("w_bits_user", direction, config.nastiXUserBits),
      ("r_valid", !direction, 1),
      ("r_ready", direction, 1),
      ("r_bits_id", !direction, config.nastiXIdBits),
      ("r_bits_resp", !direction, config.nastiXRespBits),
      ("r_bits_data", !direction, config.nastiXDataBits),
      ("r_bits_last", !direction, 1),
      ("r_bits_user", !direction, config.nastiXUserBits),
      ("b_valid", !direction, 1),
      ("b_ready", direction, 1),
      ("b_bits_id", !direction, config.nastiXIdBits),
      ("b_bits_resp", !direction, config.nastiXRespBits),
      ("b_bits_user", !direction, config.nastiXUserBits))

    ports.sorted.map { case (name, portdir, width) =>
      makePort(s"${prefix}_${name}", portdir, width) }
  }

  def makeDspBlockPorts(bits_in: Int, bits_out: Int): ModelType.Ports = {
    val config = new NastiConfig()(params)
    val streamInPorts = makeAXI4StreamPorts(s"io_in", false, bits_in)
    val streamOutPorts = makeAXI4StreamPorts(s"io_out", true, bits_out)
    val axiInPorts = makeAXI4Ports(s"io_axi", false, config)
    val globalPorts = Seq(
      makePort("clock", false, 1),
      makePort("reset", false, 1))
    val ports = new ModelType.Ports
    ports.getPort().addAll(toCollection(globalPorts ++ streamInPorts ++ streamOutPorts ++ axiInPorts))
    ports
  }

  def makeSAMPorts(bits_in: Int): ModelType.Ports = {
    val config = new NastiConfig()(params)
    val streamInPorts = makeAXI4StreamPorts(s"io_in", false, bits_in)
    val ctrlAXIPorts = makeAXI4Ports(s"io_axi", false, config)
    val dataAXIPorts = makeAXI4Ports(s"io_axi_out", false, config)
    val globalPorts = Seq(
      makePort("clock", false, 1),
      makePort("reset", false, 1))
    val ports = new ModelType.Ports
    ports.getPort().addAll(toCollection(globalPorts ++ streamInPorts ++ ctrlAXIPorts ++ dataAXIPorts))
    ports
  }

  def makeAXI4StreamInputInterface: BusInterfaceType = {
    val busType = new LibraryRefType
    busType.setVendor("amba.com")
    busType.setLibrary("AMBA4")
    busType.setName("AXI4Stream")
    busType.setVersion("r0p0_1")

    val abstractionType = new LibraryRefType
    abstractionType.setVendor("amba.com")
    abstractionType.setLibrary("AMBA4")
    abstractionType.setName("AXI4Stream_rtl")
    abstractionType.setVersion("r0p0_1")

    val portMaps = makeAXI4StreamPortMaps(s"io_in")

    val slave = new BusInterfaceType.Slave

    val busif = new BusInterfaceType
    busif.setName(s"data_in")
    busif.setBusType(busType)
    busif.setAbstractionType(abstractionType)
    busif.setPortMaps(portMaps)
    busif.setSlave(slave)
    busif
  }

  def makeAXI4StreamOutputInterface: BusInterfaceType = {
    val busType = new LibraryRefType
    busType.setVendor("amba.com")
    busType.setLibrary("AMBA4")
    busType.setName("AXI4Stream")
    busType.setVersion("r0p0_1")

    val abstractionType = new LibraryRefType
    abstractionType.setVendor("amba.com")
    abstractionType.setLibrary("AMBA4")
    abstractionType.setName("AXI4Stream_rtl")
    abstractionType.setVersion("r0p0_1")

    val portMaps = makeAXI4StreamPortMaps(s"io_out")

    val master = new BusInterfaceType.Master

    val busif = new BusInterfaceType
    busif.setName(s"data_out")
    busif.setBusType(busType)
    busif.setAbstractionType(abstractionType)
    busif.setPortMaps(portMaps)
    busif.setMaster(master)
    busif
  }

  def makeAXI4SlaveInterface(mmref: String, port: String): BusInterfaceType = {
    val busType = new LibraryRefType
    busType.setVendor("amba.com")
    busType.setLibrary("AMBA4")
    busType.setName("AXI4")
    busType.setVersion("r0p0_0")

    val abstractionType = new LibraryRefType
    abstractionType.setVendor("amba.com")
    abstractionType.setLibrary("AMBA4")
    abstractionType.setName("AXI4_rtl")
    abstractionType.setVersion("r0p0_0")

    val mmRefType = new MemoryMapRefType
    mmRefType.setMemoryMapRef(mmref)

    val slave = new BusInterfaceType.Slave
    slave.setMemoryMapRef(mmRefType)

    val portMaps = makeAXI4PortMaps(port)

    val busif = new BusInterfaceType
    busif.setName(s"axi4_slave")
    busif.setBusType(busType)
    busif.setAbstractionType(abstractionType)
    busif.setPortMaps(portMaps)
    busif.setSlave(slave)
    busif
  }

  def makeAddressSpace(name: String, size: Long): AddressSpaces.AddressSpace = {
    val addressSpace = new AddressSpaces.AddressSpace
    addressSpace.setName(name)
    var range = new BankedBlockType.Range
    range.setValue("0x" + size.toHexString)
    addressSpace.setRange(range)
    var width = new BankedBlockType.Width
    width.setValue(BigInteger.valueOf(32))
    addressSpace.setWidth(width)
    addressSpace.setAddressUnitBits(BigInteger.valueOf(8))
    addressSpace
  }

  def makeMemoryMap(name: String, baseAddr: BigInt): MemoryMapType = {
    // Generate the subspaceMaps, one for each baseAddress.
    val memoryMap = new MemoryMapType
    val addrBlocks = memoryMap.getMemoryMap()
    memoryMap.setName(name)
    val addrBlockMap = new AddressBlockType
    addrBlockMap.setName("dut")
    val baseAddress = new BaseAddress
    baseAddress.setValue("0x" + baseAddr.toString(16))
    addrBlockMap.setBaseAddress(baseAddress)
    
    val scrMap = testchipip.SCRAddressMap.contents.head._2
    val range = new BankedBlockType.Range
    range.setValue(s"${scrMap.size}")
    addrBlockMap.setRange(range)
    val width = new BankedBlockType.Width
    width.setValue(BigInteger.valueOf(64))
    addrBlockMap.setWidth(width)
    addrBlockMap.setUsage(UsageType.REGISTER)
    val registers = addrBlockMap.getRegister()
    scrMap.foreach { case(scrName, scrOffset) => 
      val register = new RegisterFile.Register
      register.setName(scrName)
      register.setAddressOffset("0x" + scrOffset.toString(16))
      val size = new RegisterFile.Register.Size
      size.setValue(BigInteger.valueOf(64))
      register.setSize(size)
      registers.add(register)
    }
    addrBlocks.add(addrBlockMap)

    memoryMap.setAddressUnitBits(BigInteger.valueOf(64))
    memoryMap
  }

  def makeFileSets(factory: ObjectFactory): FileSets = {
    val fileName = new SpiritFile.Name
    fileName.setValue(s"${longName}.v")

    val file = new SpiritFile
    file.getFileType.add(factory.createFileFileType("verilogSource"))
    file.setName(fileName)

    val fileSet = new FileSetType
    fileSet.setName("hdlSource")
    fileSet.getFile.add(file)

    val fileSets = new FileSets
    fileSets.getFileSet().add(fileSet)
    fileSets
  }

  def makeParameters(blockID: String): SpiritParameters = {
    val parameters = new SpiritParameters()
    println(s"DspBlock ID is $blockID")
    for ( (name, value) <- params(IPXACTParameters(blockID)) ) {
      println("parameter: %s, value: %s".format(name, value))
      val nameValuePairType = new NameValuePairType
      nameValuePairType.setName(name)
      val nameValuePairTypeValue = new NameValuePairType.Value
      nameValuePairTypeValue.setValue(value)
      nameValuePairType.setValue(nameValuePairTypeValue)
      parameters.getParameter().add(nameValuePairType)
    }
    parameters
  }

  def generateBlockIPXact(blockID: String) {

    println(s"Generating IP-Xact for block with ID $blockID")
    // first try GenKey
    val genExternal = try {
      Some(params(GenKey(blockID)))
    } catch {
      case e: ParameterUndefinedException => None
    }
    // then try DspBlockKey
    val dspBlockExternal = try {
      Some(params(DspBlockKey(blockID)))
    } catch {
      // case e: MatchError => None
      case e: ParameterUndefinedException => None
    }
    val (bits_in, bits_out) = (genExternal, dspBlockExternal) match {
      case (Some(gen), _) =>
        (gen.lanesIn * gen.genIn[chisel3.Data].getWidth, gen.lanesOut * gen.genOut[chisel3.Data].getWidth)
      case (None, Some(dsp)) =>
        (dsp.inputWidth, dsp.outputWidth)
      case (None, None) => throw new Exception("Didn't set parameters correctly for block-level IPXact generation")
    }
    val factory = new ObjectFactory
    val memMapName = "mm"

    val busInterfaces = new BusInterfaces
    busInterfaces.getBusInterface().addAll(toCollection(Seq(makeAXI4StreamInputInterface, makeAXI4StreamOutputInterface, makeAXI4SlaveInterface(memMapName, "io_axi"))))

    // TODO: grab base address 
    val memoryMaps = new MemoryMaps
    for (key <- DspChain.addrMapIds()) {
      if (DspChain.addrMap(key).contains(blockID)) {
        println("Adding memory map")
        memoryMaps.getMemoryMap().add(makeMemoryMap(memMapName, BigInt(DspChain.addrMap(key).port(blockID))))
      }
    }

    val model = new ModelType
    val views = new ModelType.Views
    var view = new ViewType
    var envIds = view.getEnvIdentifier
    view.setName("RTL")
    envIds.add("::")
    var verilogSource = new FileSetRef
    verilogSource.setLocalName("hdlSource")
    var fileSetRefs = view.getFileSetRef
    fileSetRefs.add(verilogSource)
    views.getView.add(view)
    model.setViews(views)
    model.setPorts(makeDspBlockPorts(bits_in, bits_out))

    val componentType = new ComponentType
    componentType.setLibrary("craft")
    componentType.setName(s"$blockID")
    componentType.setVendor("edu.berkeley.cs")
    componentType.setVersion("1.0")
    componentType.setBusInterfaces(busInterfaces)
    componentType.setMemoryMaps(memoryMaps)
    componentType.setModel(model)
    componentType.setFileSets(makeFileSets(factory))
    componentType.setParameters(makeParameters(blockID))

    val component = factory.createComponent(componentType)

    val of = new File(td, s"$blockID.xml")
    of.getParentFile().mkdirs()
    val fos = new FileOutputStream(of)
    val context = JAXBContext.newInstance(classOf[ComponentInstance])
    val marshaller = context.createMarshaller()
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
    marshaller.marshal(component, fos)
    
  }

  def generateSAMIPXact(blockID: String) {

    println(s"Generating IP-Xact for SAM with ID $blockID")
    // first try GenKey
    val genExternal = try {
      Some(params(GenKey(blockID)))
    } catch {
      case e: ParameterUndefinedException => None
    }
    // then try DspBlockKey
    val dspBlockExternal = try {
      Some(params(DspBlockKey(blockID)))
    } catch {
      // case e: MatchError => None
      case e: ParameterUndefinedException => None
    }
    val bits_in = (genExternal, dspBlockExternal) match {
      case (Some(gen), _) => gen.lanesIn * gen.genIn[chisel3.Data].getWidth
      case (None, Some(dsp)) => dsp.inputWidth
      case (None, None) => throw new Exception("Didn't set parameters correctly for block-level IPXact generation")
    }
    val factory = new ObjectFactory
    val ctrlMemMapName = "cmm"
    val dataMemMapName = "dmm"

    val busInterfaces = new BusInterfaces
    busInterfaces.getBusInterface().addAll(toCollection(Seq(makeAXI4StreamInputInterface, makeAXI4SlaveInterface(ctrlMemMapName, "io_axi"), makeAXI4SlaveInterface(dataMemMapName, "io_axi_out"))))

    // TODO: grab base address 
    val memoryMaps = new MemoryMaps
    for (key <- DspChain.addrMapIds()) {
      if (DspChain.addrMap(key).contains(blockID)) {
        if (key contains "ctrl") {
          println("Adding control memory map")
          memoryMaps.getMemoryMap().add(makeMemoryMap(ctrlMemMapName, BigInt(DspChain.addrMap(key).port(blockID))))
        } else if (key contains "data") {
          println("Adding data memory map")
          memoryMaps.getMemoryMap().add(makeMemoryMap(dataMemMapName, BigInt(DspChain.addrMap(key).port(blockID))))
        }
      }
    }

    val model = new ModelType
    val views = new ModelType.Views
    var view = new ViewType
    var envIds = view.getEnvIdentifier
    view.setName("RTL")
    envIds.add("::")
    var verilogSource = new FileSetRef
    verilogSource.setLocalName("hdlSource")
    var fileSetRefs = view.getFileSetRef
    fileSetRefs.add(verilogSource)
    views.getView.add(view)
    model.setViews(views)
    model.setPorts(makeSAMPorts(bits_in))

    val componentType = new ComponentType
    componentType.setLibrary("craft")
    componentType.setName(s"$blockID")
    componentType.setVendor("edu.berkeley.cs")
    componentType.setVersion("1.0")
    componentType.setBusInterfaces(busInterfaces)
    componentType.setMemoryMaps(memoryMaps)
    componentType.setModel(model)
    componentType.setFileSets(makeFileSets(factory))
    componentType.setParameters(makeParameters(blockID))

    val component = factory.createComponent(componentType)

    val of = new File(td, s"$blockID.xml")
    of.getParentFile().mkdirs()
    val fos = new FileOutputStream(of)
    val context = JAXBContext.newInstance(classOf[ComponentInstance])
    val marshaller = context.createMarshaller()
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
    marshaller.marshal(component, fos)
    
  }

  def generateIPXact {

    // check if this is a chain
    val (blockIDs, chain) = try {
      val chain = params(DspChainKey(params(DspChainId)))
      (chain.asInstanceOf[DspChainParameters].blocks.map{case (f, id) => id }, true)
    } catch {
      // if not, assume it's just a DSP block
      case e: ParameterUndefinedException => 
        try {
          (Seq(params(DspBlockId)), false)
        } catch {
          // if not, what is this?
          case e: ParameterUndefinedException => throw new Exception("Can't find any DSP block IDs in your design")
        }
    }

    blockIDs.foreach(generateBlockIPXact(_))
    blockIDs.foreach{x: String => generateSAMIPXact(x + ":sam")}
  }
}
