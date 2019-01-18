package ipxact

/*
import org.accellera.xmlschema.ipxact._1685_2014.{File => SpiritFile, Parameters => SpiritParameters, _}
import scala.collection.mutable.HashMap
import javax.xml.bind.{JAXBContext, Marshaller}
import java.io.{File, FileOutputStream}
import scala.collection.JavaConverters
import java.util.Collection
import java.math.BigInteger

import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._

case class IPXactParameters(id: String) extends Field[Map[String, String]]

object IPXactComponents {
  //private[dspblocks] val _ipxactComponents: scala.collection.mutable.ArrayBuffer[ComponentType] =
  val _ipxactComponents: scala.collection.mutable.ArrayBuffer[ComponentType] =
    scala.collection.mutable.ArrayBuffer.empty[ComponentType]
  def ipxactComponents(): Seq[ComponentType] = _ipxactComponents
}

trait HasIPXact {

  //////////////////////////////////////////////
  //////////// BUS INTERFACES //////////////////
  //////////////////////////////////////////////

  def toCollection[T](seq: Seq[T]): Collection[T] =
    JavaConverters.asJavaCollectionConverter(seq).asJavaCollection

  def makeBridge(count: Int, prefix: String)/*: Seq[BusInterfaceType.Slave.Bridge]*/ = {
    ???
    // (0 until count).map{i => {
    //   val bridge = new BusInterfaceType.Slave.Bridge
    //   bridge.setMasterRef(s"${prefix}_${i}")
    //   bridge.setOpaque(false)
    //   bridge
    // }}
  }

  def makePortMap(logicalPortName: String, physicalPortName: String): AbstractionTypes.AbstractionType.PortMaps.PortMap = {
    val logicalPort = new AbstractionTypes.AbstractionType.PortMaps.PortMap.LogicalPort
    logicalPort.setName(logicalPortName)

    val physicalPort = new AbstractionTypes.AbstractionType.PortMaps.PortMap.PhysicalPort
    physicalPort.setName(physicalPortName)

    val portmap = new AbstractionTypes.AbstractionType.PortMaps.PortMap
    portmap.setLogicalPort(logicalPort)
    portmap.setPhysicalPort(physicalPort)
    portmap
  }

  def makePortMaps(mappings: Seq[(String, String)]): AbstractionTypes.AbstractionType.PortMaps = {
    val portmaps = new AbstractionTypes.AbstractionType.PortMaps
    portmaps.getPortMap().addAll(toCollection(
      mappings.sorted.map { case (log, phys) => makePortMap(log, phys) }))
    portmaps
  }

  // Assumes you use ValidWithSync Chisel bundle for AXI4-Stream
  def makeAXI4StreamPortMaps(prefix: String): AbstractionTypes.AbstractionType.PortMaps = {
    makePortMaps(Seq(
      "ACLK"     -> "clock",
      "ARESETn"  -> "reset",
      "TVALID"   -> s"${prefix}_valid",
      "TLAST"    -> s"${prefix}_sync",
      "TDATA"    -> s"${prefix}_bits"))
  }

  // Assumes you use NastiIO Chisel bundle for AXI4
  def makeAXI4PortMaps(prefix: String): AbstractionTypes.AbstractionType.PortMaps = {
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
    // busif.setBusType(busType)
    // busif.setAbstractionType(abstractionType)
    // busif.setPortMaps(portMaps)
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
    // busif.setBusType(busType)
    // busif.setAbstractionType(abstractionType)
    // busif.setPortMaps(portMaps)
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
      // if (noutputs > 0) { slave.getBridge().addAll(toCollection(makeBridge(noutputs, output_prefix))) }
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
    // val baseAddress = new BaseAddress
    // baseAddress.setValue("0x" + baseAddr.toString(16))
    // addrBlockMap.setBaseAddress(baseAddress)
    
    // range
    // val range = new BankedBlockType.Range
    val rangeValue = registers.size * widthValue / 64 // assume 64-bit SCR registers
    // range.setValue(s"$rangeValue")
    // addrBlockMap.setRange(range)

    //width
    // val width = new BankedBlockType.Width
    // width.setValue(BigInteger.valueOf(widthValue))
    // addrBlockMap.setWidth(width)

    if (widthValue == 64) {
      addrBlockMap.setUsage(UsageType.REGISTER)
      // val registerBlock = addrBlockMap.getRegister()
      registers.foreach { case(mname: String, addr: BigInt) => 
        val register = new RegisterFile.Register
        register.setName(mname)
        val offset = addr-baseAddr
        // register.setAddressOffset("0x" + offset.toString(16))
        // val size = new RegisterFile.Register.Size
        // size.setValue(BigInteger.valueOf(widthValue))
        // register.setSize(size)
        // registerBlock.add(register)
      }
    } else {
      addrBlockMap.setUsage(UsageType.MEMORY)
      addrBlockMap.setAccess(AccessType.READ_ONLY) 
      addrBlockMap.setVolatile(true) 
    }
    addrBlockMap
  }

  def makeSubspaceRef(name: String, baseAddr: BigInt, signal: String, segmentName: String): SubspaceRefType = {
    val subspaceRef = new SubspaceRefType
    subspaceRef.setMasterRef(signal)
    subspaceRef.setName(name)
    // val baseAddress = new BaseAddress
    // baseAddress.setValue("0x" + baseAddr.toString(16))
    // subspaceRef.setBaseAddress(baseAddress)
    subspaceRef.setSegmentRef(segmentName)
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
    // memoryMap.setAddressUnitBits(BigInteger.valueOf(8))
    memoryMap
  }


  //////////////////////////////////////////////
  //////////// Address Spaces //////////////////
  //////////////////////////////////////////////

  // asref = address space name reference string
  def makeAddressSpace(asref: String, segments: AddressSpaces.AddressSpace.Segments): AddressSpaces.AddressSpace = {
    val addressSpace = new AddressSpaces.AddressSpace
    addressSpace.setName(asref)
    // var range = new BankedBlockType.Range
    // range should be large enough to accommodate all the segments (offset + range)
    //var size = segments.getSegment().asScala.foldLeft(BigInt(0))((b,a) => {
    //  val segmentOffset = BigInt.apply(a.getAddressOffset().getValue().substring(2), 16)
    //  val segmentRange = BigInt.apply(a.getRange().getValue().substring(2), 16)
    //  val segmentMaxAddress = segmentOffset + segmentRange
    //  b.max(segmentMaxAddress)
    //})
    //range.setValue("0x" + size.toString(16))
    // range.setValue("4G")
    // addressSpace.setRange(range)
    // var width = new BankedBlockType.Width
    // width.setValue(BigInteger.valueOf(64))
    // addressSpace.setWidth(width)
    // addressSpace.setAddressUnitBits(BigInteger.valueOf(8))
    addressSpace.setSegments(segments)
    addressSpace
  }

  // create a segment within an address space 
  def makeAddressSpaceSegment(name: String, size: BigInt, offset: BigInt): AddressSpaces.AddressSpace.Segments.Segment = {
    val segment = new AddressSpaces.AddressSpace.Segments.Segment
    segment.setName(name)
    // var addressOffset = new AddressSpaces.AddressSpace.Segments.Segment.AddressOffset
    // addressOffset.setValue("0x" + offset.toString(16))
    // segment.setAddressOffset(addressOffset)
    // var range = new AddressSpaces.AddressSpace.Segments.Segment.Range
    // range.setValue("0x" + size.toString(16))
    // segment.setRange(range)
    segment
  }

  // combine segments
  def makeAddressSpaceSegments(segmentsList: Seq[AddressSpaces.AddressSpace.Segments.Segment]): AddressSpaces.AddressSpace.Segments = {
    val segments = new AddressSpaces.AddressSpace.Segments
    segments.getSegment().addAll(toCollection(segmentsList))
    segments
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
      // val left = new Vector.Left
      // val right = new Vector.Right
      // left.setValue(BigInteger.valueOf(width - 1))
      // right.setValue(BigInteger.valueOf(0))
      // vector.setLeft(left)
      // vector.setRight(right)
      // wire.setVector(vector)
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

  def makeAXI4Ports(prefix: String, direction: Boolean): Seq[PortType] = {
    val ports = Seq(
      ("ar_valid", direction, 1),
      ("ar_ready", !direction, 1),
      // ("ar_bits_id", direction, config.nastiXIdBits),
      // ("ar_bits_addr", direction, config.nastiXAddrBits),
      // ("ar_bits_size", direction, config.nastiXSizeBits),
      // ("ar_bits_len", direction, config.nastiXLenBits),
      // ("ar_bits_burst", direction, config.nastiXBurstBits),
      ("ar_bits_lock", direction, 1),
      // ("ar_bits_cache", direction, config.nastiXCacheBits),
      // ("ar_bits_prot", direction, config.nastiXProtBits),
      // ("ar_bits_qos", direction, config.nastiXQosBits),
      // ("ar_bits_region", direction, config.nastiXRegionBits),
      // ("ar_bits_user", direction, config.nastiXUserBits),
      ("aw_valid", direction, 1),
      ("aw_ready", !direction, 1),
      // ("aw_bits_id", direction, config.nastiXIdBits),
      // ("aw_bits_addr", direction, config.nastiXAddrBits),
      // ("aw_bits_size", direction, config.nastiXSizeBits),
      // ("aw_bits_len", direction, config.nastiXLenBits),
      // ("aw_bits_burst", direction, config.nastiXBurstBits),
      ("aw_bits_lock", direction, 1),
      // ("aw_bits_cache", direction, config.nastiXCacheBits),
      // ("aw_bits_prot", direction, config.nastiXProtBits),
      // ("aw_bits_qos", direction, config.nastiXQosBits),
      // ("aw_bits_region", direction, config.nastiXRegionBits),
      // ("aw_bits_user", direction, config.nastiXUserBits),
      ("w_valid", direction, 1),
      ("w_ready", !direction, 1),
      // ("w_bits_data", direction, config.nastiXDataBits),
      // ("w_bits_strb", direction, config.nastiWStrobeBits),
      ("w_bits_last", direction, 1),
      // ("w_bits_user", direction, config.nastiXUserBits),
      ("r_valid", !direction, 1),
      ("r_ready", direction, 1),
      // ("r_bits_id", !direction, config.nastiXIdBits),
      // ("r_bits_resp", !direction, config.nastiXRespBits),
      // ("r_bits_data", !direction, config.nastiXDataBits),
      ("r_bits_last", !direction, 1),
      // ("r_bits_user", !direction, config.nastiXUserBits),
      ("b_valid", !direction, 1),
      ("b_ready", direction, 1),
      // ("b_bits_id", !direction, config.nastiXIdBits),
      // ("b_bits_resp", !direction, config.nastiXRespBits),
      // ("b_bits_user", !direction, config.nastiXUserBits)
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
    var view = new ModelType.Views.View
    var envIds = view.getEnvIdentifier
    view.setName("RTL")
    val envId = new ModelType.Views.View.EnvIdentifier
    envId.setValue("::")
    envIds.add(envId)
    var verilogSource = new FileSetRef
    verilogSource.setLocalName("hdlSource")
    // var fileSetRefs = view.getFileSetRef
    // fileSetRefs.add(verilogSource)
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
      val fileName = new StringURIExpression
      fileName.setValue(s"$fileString")
      val file = new SpiritFile
      val fileType = factory.createFileType()
      fileType.setId("verilogSource")
      file.getFileType.add(fileType)
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
      // val nameValuePairTypeValue = new NameValuePairType.Value
      // nameValuePairTypeValue.setValue(value)
      // nameValuePairType.setValue(nameValuePairTypeValue)
      // parameters.getParameter().add(nameValuePairType)
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


trait HasDspIPXact extends HasIPXact {

  //////////////////////////////////////////////
  //////////// BUS INTERFACES //////////////////
  //////////////////////////////////////////////

  def makeDspBlockInterfaces(mmref: String): BusInterfaces = {
    val streamInInterface = makeAXI4StreamInterface("io_in", "data_in", false)
    val streamOutInterface = makeAXI4StreamInterface("io_out", "data_out", true)
    val axiInterface = makeAXI4Interface(mmref, "io_axi", "axi4_slave", false)

    val busInterfaces = new BusInterfaces
    busInterfaces.getBusInterface().addAll(toCollection(Seq(streamInInterface, streamOutInterface, axiInterface)))
    busInterfaces
  }

  def makeSAMInterfaces(ctrl_mmref: String, data_mmref: String): BusInterfaces = {
    val streamInInterface = makeAXI4StreamInterface("io_in", "data_in", false)
    val ctrlAXIInterface = makeAXI4Interface(ctrl_mmref, "io_axi", "axi4_slave", false)
    val dataAXIInterface = makeAXI4Interface(data_mmref, "io_axi_out", "axi4_data_slave", false)

    val busInterfaces = new BusInterfaces
    busInterfaces.getBusInterface().addAll(toCollection(Seq(streamInInterface, ctrlAXIInterface, dataAXIInterface)))
    busInterfaces
  }

  def makeXbarInterfaces(inputs: Int, outputs: Int, mmref: String, asref: String): BusInterfaces = {
    val inputInterfaces = (0 until inputs).map(i => makeAXI4Interface(s"${mmref}_${i}", s"io_in_${i}", s"io_in_${i}", false, outputs, "io_out"))
    val outputInterfaces = (0 until outputs).map(i => makeAXI4Interface(s"${asref}_${i}", s"io_out_${i}", s"io_out_${i}", true))

    val busInterfaces = new BusInterfaces
    busInterfaces.getBusInterface().addAll(toCollection(Seq(inputInterfaces, outputInterfaces).flatten))
    busInterfaces
  }

  def makeSCRInterfaces(mmref: String): BusInterfaces = {
    val ctrlAXIInterface = makeAXI4Interface(mmref, "io_nasti", "axi4_slave", false)

    val busInterfaces = new BusInterfaces
    busInterfaces.getBusInterface().add(ctrlAXIInterface)
    busInterfaces
  }


  //////////////////////////////////////////////
  //////////// Memory Maps /////////////////////
  //////////////////////////////////////////////

  def makeDspBlockMemoryMaps(mmref: String, baseAddress: BigInt, registers: HashMap[String, BigInt]): MemoryMaps = {
    val addrBlock = makeAddressBlock("SCRFile", baseAddress, registers, 64)
    val memoryMaps = new MemoryMaps
    memoryMaps.getMemoryMap().add(makeMemoryMap(mmref, addrBlocks=Seq(addrBlock)))
    memoryMaps
  }

  // TODO: make the data map correct
  def makeSAMMemoryMaps(ctrl_mmref: String, data_mmref: String, ctrl_baseAddress: BigInt, data_baseAddress: BigInt, registers: HashMap[String, BigInt], width: Int): MemoryMaps = {
    val ctrl_addrBlock = makeAddressBlock("SCRFile", ctrl_baseAddress, registers, 64)
    val data_addrBlock = makeAddressBlock("SAMData", data_baseAddress, HashMap(("SAM", data_baseAddress)), width)
    val memoryMaps = new MemoryMaps
    memoryMaps.getMemoryMap().addAll(toCollection(Seq(makeMemoryMap(ctrl_mmref, addrBlocks=Seq(ctrl_addrBlock)), makeMemoryMap(data_mmref, addrBlocks=Seq(data_addrBlock)))))
    memoryMaps
  }

  def makeXbarMemoryMaps(mmref: String, asref: String, inputs: Int, addrMap: AddressSet): MemoryMaps = {
    val memoryMaps = new MemoryMaps
    // memoryMaps.getMemoryMap().addAll(toCollection(
    //   (0 until inputs).map(i => makeMemoryMap(s"${mmref}_${i}", subspaceRefs=addrMap.flatten.zipWithIndex.map{ case(entry, j) =>
    //     makeSubspaceRef(s"subspacemap_${mmref}_${i}_io_out_${j}", entry.region.start, s"io_out_${j}", s"${asref}_${j}_segment")
    //   }))
    // ))
    memoryMaps
  }

  def makeSCRMemoryMaps(mmref: String, baseAddress: BigInt, registers: HashMap[String, BigInt]): MemoryMaps = {
    val addrBlock = makeAddressBlock("SCRFile", baseAddress, registers, 64)
    val memoryMaps = new MemoryMaps
    memoryMaps.getMemoryMap().add(makeMemoryMap(mmref, addrBlocks=Seq(addrBlock)))
    memoryMaps
  }

  //////////////////////////////////////////////
  //////////// Address Spaces //////////////////
  //////////////////////////////////////////////

  def makeDspBlockAddressSpaces: AddressSpaces = {
    new AddressSpaces
  }

  def makeSAMAddressSpaces: AddressSpaces = {
    new AddressSpaces
  }

  def makeXbarAddressSpaces(ref: String, addrMap: AddressSet): AddressSpaces = {
    val addressSpaces = new AddressSpaces
    // addressSpaces.getAddressSpace.addAll(toCollection(
    //   addrMap.flatten.zipWithIndex.map { case (entry, i) =>
    //     makeAddressSpace(s"${ref}_${i}",
    //       makeAddressSpaceSegments(
    //         Seq(makeAddressSpaceSegment(s"${ref}_${i}_segment", entry.region.size, entry.region.start))
    //       )
    //     )
    //   }
    // ))
    addressSpaces
  }

  def makeSCRAddressSpaces: AddressSpaces = {
    new AddressSpaces
  }


  //////////////////////////////////////////////
  //////////// Model ///////////////////////////
  //////////////////////////////////////////////

  // assumes DSP block, stream in and out and AXI4 control
  def makeDspBlockPorts(bits_in: Int, bits_out: Int): ModelType.Ports = {
    val streamInPorts = makeAXI4StreamPorts("io_in", false, bits_in)
    val streamOutPorts = makeAXI4StreamPorts("io_out", true, bits_out)
    val axiPorts = makeAXI4Ports(s"io_axi", false)
    val globalPorts = makeClockAndResetPorts

    val ports = new ModelType.Ports
    ports.getPort().addAll(toCollection(globalPorts ++ streamInPorts ++ streamOutPorts ++ axiPorts))
    ports
  }

  // assumes SAM block, stream in and AXI4 control and data out
  def makeSAMPorts(bits_in: Int)(implicit p: Parameters): ModelType.Ports = {
    // TODO: do we need to separate control from data AXI interface configs here?
    val streamInPorts = makeAXI4StreamPorts(s"io_in", false, bits_in)
    val ctrlAXIPorts = makeAXI4Ports(s"io_axi", false)
    val dataAXIPorts = makeAXI4Ports(s"io_axi_out", false)
    val globalPorts = makeClockAndResetPorts

    val ports = new ModelType.Ports
    ports.getPort().addAll(toCollection(globalPorts ++ streamInPorts ++ ctrlAXIPorts ++ dataAXIPorts))
    ports
  }

  def makeXbarPorts(inputs: Int, outputs: Int): ModelType.Ports = {
    val inPorts = (0 until inputs).flatMap(i => makeAXI4Ports(s"io_in_${i}", false))
    val outPorts = (0 until outputs).flatMap(i => makeAXI4Ports(s"io_out_${i}", true))
    val globalPorts = makeClockAndResetPorts

    val ports = new ModelType.Ports
    ports.getPort().addAll(toCollection(globalPorts ++ inPorts ++ outPorts))
    ports
  }

  def makeSCRPorts()(implicit p: Parameters): ModelType.Ports = {
    val AXIPorts: Seq[PortType] = makeAXI4Ports(s"io_axi", false)
    val globalPorts = makeClockAndResetPorts

    val ports = new ModelType.Ports
    ports.getPort().addAll(toCollection(globalPorts ++ AXIPorts))
    ports
  }

  //////////////////////////////////////////////
  //////////// Component ///////////////////////
  //////////////////////////////////////////////

  def makeDspBlockComponent(bits_in: Int, bits_out: Int, _baseAddress: BigInt, uuid: Int, name: String)(implicit p: Parameters): ComponentType = {
    val mmref = s"${name}_mm"
    val baseAddress = _baseAddress
    val registers: HashMap[String, BigInt] = ??? // = testchipip.SCRAddressMap(p(DspBlockId)).getOrElse(new HashMap[String, BigInt])
    val busInterfaces = makeDspBlockInterfaces(mmref)
    val addressSpaces = makeDspBlockAddressSpaces
    val memoryMaps = makeDspBlockMemoryMaps(mmref, baseAddress, registers)
    val model = makeModel(makeDspBlockPorts(bits_in, bits_out))
    val parameterMap = HashMap[String, String]()
    // parameterMap ++= p(IPXactParameters(p(DspBlockId)))
    parameterMap ++= List(("uuid", uuid.toString))
    val parameters = makeParameters(parameterMap)
    makeComponent(name, busInterfaces, addressSpaces, memoryMaps, model, parameters)
  }

  def makeSAMComponent(bits_in: Int, _ctrl_baseAddress: BigInt, _data_baseAddress: BigInt, memDepth: Int, uuid: Int, name: String)(implicit p: Parameters): ComponentType = {
    val ctrl_mmref = s"${name}_ctrl_mm"
    val data_mmref = s"${name}_data_mm"
    val ctrl_baseAddress = _ctrl_baseAddress
    val data_baseAddress = _data_baseAddress
    val registers: HashMap[String, BigInt] = ??? // testchipip.SCRAddressMap(p(DspBlockId)).getOrElse(new HashMap[String, BigInt])
    val busInterfaces = makeSAMInterfaces(ctrl_mmref, data_mmref)
    val addressSpaces = makeSAMAddressSpaces
    val memoryMaps = makeSAMMemoryMaps(ctrl_mmref, data_mmref, ctrl_baseAddress, data_baseAddress, registers, memDepth)
    val model = makeModel(makeSAMPorts(bits_in))
    val parameterMap = HashMap[String, String]()
    // parameterMap ++= p(IPXactParameters(p(DspBlockId)))
    parameterMap ++= List(("uuid", uuid.toString))
    val parameters = makeParameters(parameterMap)
    makeComponent(name, busInterfaces, addressSpaces, memoryMaps, model, parameters)
  }

  def makeXbarComponent(name: String, addrMap: AddressSet, inputs: Int, outputs: Int, usePortQueues: Boolean): ComponentType = {
    val mmref = s"${name}_mm"
    val asref = s"${name}_as"
    val busInterfaces = makeXbarInterfaces(inputs, outputs, mmref, asref)
    val addressSpaces = makeXbarAddressSpaces(asref, addrMap)
    val memoryMaps = makeXbarMemoryMaps(mmref, asref, inputs, addrMap)
    val model = makeModel(makeXbarPorts(inputs, outputs))
    val parameters = makeParameters(HashMap[String, String](
      "usePortQueues" -> usePortQueues.toString))
    makeComponent(name, busInterfaces, addressSpaces, memoryMaps, model, parameters)
  }

  def makeSCRComponent(_baseAddress: BigInt, id: String, name: String)(implicit p: Parameters): ComponentType = {
    val mmref = s"${name}_mm"
    val baseAddress = _baseAddress
    val registers: HashMap[String, BigInt] = ??? // = testchipip.SCRAddressMap(id).getOrElse(new HashMap[String, BigInt])
    val busInterfaces = makeSCRInterfaces(mmref)
    val addressSpaces = makeSCRAddressSpaces
    val memoryMaps = makeSCRMemoryMaps(mmref, baseAddress, registers)
    val model = makeModel(makeSCRPorts)
    val parameters = makeParameters(HashMap[String, String]())
    makeComponent(name, busInterfaces, addressSpaces, memoryMaps, model, parameters)
  }
}

object DspIPXact extends HasDspIPXact
*/