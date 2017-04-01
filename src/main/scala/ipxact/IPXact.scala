package ipxact

import util.GeneratorApp
import org.accellera.spirit.v1685_2009.{File => SpiritFile, Parameters => SpiritParameters, _}
import javax.xml.bind.{JAXBContext, Marshaller}
import java.io.{File, FileOutputStream}
import scala.collection.JavaConverters
import java.util.Collection
import java.math.BigInteger
import junctions._
import rocketchip._
import scala.collection.mutable.HashMap
import cde._

trait HasIPXact {

  //////////////////////////////////////////////
  //////////// BUS INTERFACES //////////////////
  //////////////////////////////////////////////

  def toCollection[T](seq: Seq[T]): Collection[T] =
    JavaConverters.asJavaCollectionConverter(seq).asJavaCollection

  def makeBridge(count: Int, prefix: String): Seq[BusInterfaceType.Slave.Bridge] = {
    (0 until count).map{i => {
      val bridge = new BusInterfaceType.Slave.Bridge
      bridge.setMasterRef(s"${prefix}_${i}")
      bridge.setOpaque(false)
      bridge
    }}
  }

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

  // prefix is the beginning of the wire, e.g. io_in
  // ifname (interface name) can be whatever you want, e.g. data_in
  // direction is output = true, input = false
  def makeAXI4StreamInterface(prefix: String, ifname: String, direction: Boolean): BusInterfaceType = {
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

    val portMaps = makeAXI4StreamPortMaps(prefix)

    val busif = new BusInterfaceType
    busif.setName(ifname)
    busif.setBusType(busType)
    busif.setAbstractionType(abstractionType)
    busif.setPortMaps(portMaps)
    if (direction) {
      val master = new BusInterfaceType.Master
      busif.setMaster(master)
    } else {
      val slave = new BusInterfaceType.Slave
      busif.setSlave(slave)
    }

    busif
  }

  // ref = either memory map name reference string or address space name reference string
  // prefix is the beginning of the wire, e.g. io_axi_in
  // ifname (interface name) can be whatever you want, e.g. data_in
  // direction is output = true, input = false
  // noutputs = if this is an input interface, creates an output bridge for this many outputs
  def makeAXI4Interface(ref: String, prefix: String, ifname: String, direction: Boolean, noutputs: Int = 0, output_prefix: String = ""): BusInterfaceType = {
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

    val portMaps = makeAXI4PortMaps(prefix)

    val busif = new BusInterfaceType
    busif.setName(ifname)
    busif.setBusType(busType)
    busif.setAbstractionType(abstractionType)
    busif.setPortMaps(portMaps)
    if (direction) {
      // TODO: is this right?
      val addrSpaceRef = new BusInterfaceType.Master.AddressSpaceRef
      addrSpaceRef.setAddressSpaceRef(ref)
      val master = new BusInterfaceType.Master
      master.setAddressSpaceRef(addrSpaceRef)
      if (noutputs > 0) { 
        println("Warning: You tried to create an output bridge for an output AXI4 interface. Ignoring.")
      }
      busif.setMaster(master)
    } else {
      val mmRefType = new MemoryMapRefType
      mmRefType.setMemoryMapRef(ref)
      val slave = new BusInterfaceType.Slave
      slave.setMemoryMapRef(mmRefType)
      if (noutputs > 0) { slave.getBridge().addAll(toCollection(makeBridge(noutputs, output_prefix))) }
      busif.setSlave(slave)
    }
    busif
  }

  //////////////////////////////////////////////
  //////////// Memory Maps /////////////////////
  //////////////////////////////////////////////

  // name = name of the address block
  // base address = base address of the address block
  // registers = list of register names, each assumed to each be widthValue wide
  def makeAddressBlock(name: String, baseAddr: BigInt, registers: HashMap[String, BigInt], widthValue: Int): AddressBlockType = {
    val addrBlockMap = new AddressBlockType
    addrBlockMap.setName(name)
    val baseAddress = new BaseAddress
    baseAddress.setValue("0x" + baseAddr.toString(16))
    addrBlockMap.setBaseAddress(baseAddress)
    
    // range
    val range = new BankedBlockType.Range
    val rangeValue = registers.size * widthValue / 64 // assume 64-bit addressUnitBits
    range.setValue(s"$rangeValue")
    addrBlockMap.setRange(range)

    //width
    val width = new BankedBlockType.Width
    width.setValue(BigInteger.valueOf(widthValue))
    addrBlockMap.setWidth(width)

    if (widthValue == 64) {
      addrBlockMap.setUsage(UsageType.REGISTER)
      val registerBlock = addrBlockMap.getRegister()
      registers.foreach { case(mname: String, addr: BigInt) => 
        val register = new RegisterFile.Register
        register.setName(mname)
        val offset = addr-baseAddr
        register.setAddressOffset("0x" + offset.toString(16))
        val size = new RegisterFile.Register.Size
        size.setValue(BigInteger.valueOf(widthValue))
        register.setSize(size)
        registerBlock.add(register)
      }
    } else {
      addrBlockMap.setUsage(UsageType.MEMORY)
      addrBlockMap.setAccess(AccessType.READ_ONLY) 
      addrBlockMap.setVolatile(true) 
    }
    addrBlockMap
  }

  def makeSubspaceRef(name: String, baseAddr: BigInt, signal: String): SubspaceRefType = {
    val subspaceRef = new SubspaceRefType
    subspaceRef.setMasterRef(signal)
    subspaceRef.setName(name)
    val baseAddress = new BaseAddress
    baseAddress.setValue("0x" + baseAddr.toString(16))
    subspaceRef.setBaseAddress(baseAddress)
    subspaceRef
  }

  // assumes just one address block for now
  // mmref = memory map name reference string
  // addrBlocks = list of address blocks in this memory map
  // subspaceRefs = list of subspace references in this memory map
  def makeMemoryMap(mmref: String, addrBlocks: Seq[AddressBlockType] = Seq(), subspaceRefs: Seq[SubspaceRefType] = Seq()): MemoryMapType = {
    // Generate the subspaceMaps, one for each baseAddress.
    val memoryMap = new MemoryMapType
    memoryMap.setName(mmref)
    addrBlocks.foreach(memoryMap.getMemoryMap().add(_))
    subspaceRefs.foreach(memoryMap.getMemoryMap().add(_))
    memoryMap.setAddressUnitBits(BigInteger.valueOf(64))
    memoryMap
  }


  //////////////////////////////////////////////
  //////////// Address Spaces //////////////////
  //////////////////////////////////////////////

  // mmref = address space name reference string
  def makeAddressSpace(asref: String, size: BigInt): AddressSpaces.AddressSpace = {
    val addressSpace = new AddressSpaces.AddressSpace
    addressSpace.setName(asref)
    var range = new BankedBlockType.Range
    range.setValue("0x" + size.toString(16))
    addressSpace.setRange(range)
    var width = new BankedBlockType.Width
    width.setValue(BigInteger.valueOf(64))
    addressSpace.setWidth(width)
    addressSpace.setAddressUnitBits(BigInteger.valueOf(8))
    addressSpace
  }


  //////////////////////////////////////////////
  //////////// Model ///////////////////////////
  //////////////////////////////////////////////

  // direction: output = true, input = false
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

  // direction: output = true, input = false
  def makeAXI4StreamPorts(prefix: String, direction: Boolean, bits: Int): Seq[PortType] = {
    val ports = Seq(
      ("valid", direction, 1),
      ("sync", direction, 1),
      ("bits", direction, bits)
    )

    ports.sorted.map { case (name, portdir, width) =>
      makePort(s"${prefix}_${name}", portdir, width) 
    }
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
      ("b_bits_user", !direction, config.nastiXUserBits)
    )

    ports.sorted.map { case (name, portdir, width) =>
      makePort(s"${prefix}_${name}", portdir, width) 
    }
  }

  def makeClockAndResetPorts: Seq[PortType] = {
    val ports = Seq(
      ("clock", false, 1),
      ("reset", false, 1)
    )

    ports.sorted.map { case (name, portdir, width) =>
      makePort(s"${name}", portdir, width) 
    }
  }

  // TODO: what goes in here?
  def makeViews: ModelType.Views = { 
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
    views
  }

  def makeModel(ports: ModelType.Ports): ModelType = {
    val model = new ModelType
    model.setViews(makeViews)
    model.setPorts(ports)
    model
  }


  //////////////////////////////////////////////
  //////////// File Sets ///////////////////////
  //////////////////////////////////////////////

  // files = list of Verilog filenames
  // assumes just one file set
  def makeFileSets(file: String): FileSets = makeFileSets(Seq(file))
  def makeFileSets(files: Seq[String]): FileSets = {

    val factory = new ObjectFactory
    val fileSet = new FileSetType
    fileSet.setName("hdlSource")

    files.foreach { fileString: String => {
      val fileName = new SpiritFile.Name
      fileName.setValue(s"$fileString")
      val file = new SpiritFile
      file.getFileType.add(factory.createFileFileType("verilogSource"))
      file.setName(fileName)
      fileSet.getFile.add(file)
    }}

    val fileSets = new FileSets
    fileSets.getFileSet().add(fileSet)
    fileSets
  }

  //////////////////////////////////////////////
  //////////// Parameters //////////////////////
  //////////////////////////////////////////////

  def makeParameters(parameterMap: HashMap[String, String]): SpiritParameters = {
    val parameters = new SpiritParameters()
    for ( (name, value) <- parameterMap ) {
      val nameValuePairType = new NameValuePairType
      nameValuePairType.setName(name)
      val nameValuePairTypeValue = new NameValuePairType.Value
      nameValuePairTypeValue.setValue(value)
      nameValuePairType.setValue(nameValuePairTypeValue)
      parameters.getParameter().add(nameValuePairType)
    }
    parameters
  }

  //////////////////////////////////////////////
  //////////// Component ///////////////////////
  //////////////////////////////////////////////

  def makeComponent(
    name: String, 
    busInterfaces: BusInterfaces, 
    addressSpaces: AddressSpaces, 
    memoryMaps: MemoryMaps, 
    model: ModelType, 
    parameters: SpiritParameters
  ): ComponentType = {
    val componentType = new ComponentType
    componentType.setLibrary("craft")
    componentType.setName(name)
    componentType.setVendor("edu.berkeley.cs")
    componentType.setVersion("1.0")
    componentType.setBusInterfaces(busInterfaces)
    componentType.setAddressSpaces(addressSpaces)
    componentType.setMemoryMaps(memoryMaps)
    componentType.setModel(model)
    componentType.setParameters(parameters)
    componentType
  }



}

object IPXact extends HasIPXact
