package ipxact

import org.accellera.spirit.v1685_2009.{File => SpiritFile, Parameters => SpiritParameters, _}

import scala.collection.{JavaConverters, mutable}
import java.math.BigInteger
import java.util

import chisel3.core.{ActualDirection, Aggregate, DataMirror}
import chisel3.{Data, Element, Module, Record, Vec}
import dspblocks.{AXI4DspBlock, AXI4HasCSR, CSR, CSRModule}
import freechips.rocketchip.amba.axi4.AXI4Bundle
import freechips.rocketchip.amba.axi4stream.AXI4StreamBundle

trait HasIPXact {

  //////////////////////////////////////////////
  //////////// BUS INTERFACES //////////////////
  //////////////////////////////////////////////

  def toCollection[T](seq: Seq[T]): util.Collection[T] =
    JavaConverters.asJavaCollectionConverter(seq).asJavaCollection

  def makeBridge(count: Int, prefix: String): Seq[BusInterfaceType.Slave.Bridge] = {
    (0 until count).map{i => {
      val bridge = new BusInterfaceType.Slave.Bridge
      bridge.setMasterRef(s"${prefix}_$i")
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
    portmaps.getPortMap.addAll(toCollection(
      mappings.sorted.map { case (log, phys) => makePortMap(log, phys) }))
    portmaps
  }

  // Assumes you use Paul's AXI4-Stream bundle
  def makeAXI4StreamPortMaps(prefix: String, port: AXI4StreamBundle): BusInterfaceType.PortMaps = {
    // TODO add other signals
    val signalMap = Seq(
      "ACLK"     -> "clock"                 -> 1,
      "ARESETn"  -> "reset"                 -> 1,
      "TVALID"   -> s"${prefix}_valid"      -> 1,
      "TLAST"    -> s"${prefix}_sync"       -> 1,
      "TDATA"    -> s"${prefix}_bits_data"  -> (if (port.params.hasData) 8 * port.params.n else 0),
      "TSTRB"    -> s"${prefix}_bits_strb"  -> (if (port.params.hasStrb) port.params.n else 0),
      "TKEEP"    -> s"${prefix}_bits_keep"  -> (if (port.params.hasKeep) port.params.n else 0),
      "TID"      -> s"${prefix}_bits_id"    -> port.params.i,
      "TDEST"    -> s"${prefix}_bits_dest"  -> port.params.d,
      "TUSER"    -> s"${prefix}_bits_user"  -> port.params.u
    ).collect({ case (pair, width) if width > 0 => pair })
    makePortMaps(signalMap)
  }

  // Assumes you use Rocket's AXI4 bundle
  def makeAXI4PortMaps(prefix: String, port: AXI4Bundle): BusInterfaceType.PortMaps = {
    val p = port.params
    val signalMap = Seq(
      "ACLK"     -> "clock"                     -> 1,
      "ARESETn"  -> "reset"                     -> 1,
      "ARVALID"  -> s"${prefix}_ar_valid"       -> 1,
      "ARREADY"  -> s"${prefix}_ar_ready"       -> 1,
      "ARID"     -> s"${prefix}_ar_bits_id"     -> p.idBits,
      "ARADDR"   -> s"${prefix}_ar_bits_addr"   -> p.addrBits,
      "ARSIZE"   -> s"${prefix}_ar_bits_size"   -> p.sizeBits,
      "ARLEN"    -> s"${prefix}_ar_bits_len"    -> p.lenBits,
      "ARBURST"  -> s"${prefix}_ar_bits_burst"  -> p.burstBits,
      "ARPROT"   -> s"${prefix}_ar_bits_prot"   -> p.protBits,
      "ARLOCK"   -> s"${prefix}_ar_bits_lock"   -> p.lockBits,
      "ARQOS"    -> s"${prefix}_ar_bits_qos"    -> p.qosBits,
      // "ARREGION" -> s"${prefix}_ar_bits_region" -> p.???,
      "ARCACHE"  -> s"${prefix}_ar_bits_cache"  -> p.cacheBits,
      "ARUSER"   -> s"${prefix}_ar_bits_user"   -> p.userBits,
      "AWVALID"  -> s"${prefix}_aw_valid"       -> 1,
      "AWREADY"  -> s"${prefix}_aw_ready"       -> 1,
      "AWID"     -> s"${prefix}_aw_bits_id"     -> p.idBits,
      "AWADDR"   -> s"${prefix}_aw_bits_addr"   -> p.addrBits,
      "AWSIZE"   -> s"${prefix}_aw_bits_size"   -> p.sizeBits,
      "AWLEN"    -> s"${prefix}_aw_bits_len"    -> p.lenBits,
      "AWBURST"  -> s"${prefix}_aw_bits_burst"  -> p.burstBits,
      "AWPROT"   -> s"${prefix}_aw_bits_prot"   -> p.protBits,
      "AWLOCK"   -> s"${prefix}_aw_bits_lock"   -> p.lockBits,
      "AWQOS"    -> s"${prefix}_aw_bits_qos"    -> p.qosBits,
      // "AWREGION" -> s"${prefix}_aw_bits_region" -> p.???,
      "AWCACHE"  -> s"${prefix}_aw_bits_cache"  -> p.cacheBits,
      "AWUSER"   -> s"${prefix}_aw_bits_user"   -> p.userBits,
      "WVALID"   -> s"${prefix}_w_valid"        -> 1,
      "WREADY"   -> s"${prefix}_w_ready"        -> 1,
      "WDATA"    -> s"${prefix}_w_bits_data"    -> p.dataBits,
      "WSTRB"    -> s"${prefix}_w_bits_strb"    -> p.dataBits / 8,
      "WLAST"    -> s"${prefix}_w_bits_last"    -> 1,
      "WUSER"    -> s"${prefix}_w_bits_user"    -> p.userBits,
      "RVALID"   -> s"${prefix}_r_valid"        -> 1,
      "RREADY"   -> s"${prefix}_r_ready"        -> 1,
      "RID"      -> s"${prefix}_r_bits_id"      -> p.idBits,
      "RRESP"    -> s"${prefix}_r_bits_resp"    -> p.respBits,
      "RDATA"    -> s"${prefix}_r_bits_data"    -> p.dataBits,
      "RLAST"    -> s"${prefix}_r_bits_last"    -> 1,
      "RUSER"    -> s"${prefix}_r_bits_user"    -> p.userBits,
      "BVALID"   -> s"${prefix}_b_valid"        -> 1,
      "BREADY"   -> s"${prefix}_b_ready"        -> 1,
      "BID"      -> s"${prefix}_b_bits_id"      -> p.idBits,
      "BRESP"    -> s"${prefix}_b_bits_resp"    -> p.respBits,
      "BUSER"    -> s"${prefix}_b_bits_user"    -> p.userBits
    ).collect({ case (pair, width) if width > 0 => pair })
    makePortMaps(signalMap)
  }

  // prefix is the beginning of the wire, e.g. io_in
  // ifname (interface name) can be whatever you want, e.g. data_in
  // direction is output = true, input = false
  def makeAXI4StreamInterface(prefix: String, port: AXI4StreamBundle, ifname: String, direction: Boolean): BusInterfaceType = {
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

    val portMaps = makeAXI4StreamPortMaps(prefix, port)

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
  def makeAXI4Interface(ref: String, prefix: String, port: AXI4Bundle, ifname: String, direction: Boolean, noutputs: Int = 0, output_prefix: String = ""): BusInterfaceType = {
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

    val portMaps = makeAXI4PortMaps(prefix, port)

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
      if (noutputs > 0) { slave.getBridge.addAll(toCollection(makeBridge(noutputs, output_prefix))) }
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
  def makeAddressBlock(name: String, baseAddr: BigInt, module: AXI4HasCSR): AddressBlockType = { //registers: scala.collection.Map[String, CSR.RegInfo]): AddressBlockType = {
    val registers = module.csrMap
    val widthValue: Int = registers.map { case (_, (_, w, _)) => w.get }.max
    assert(widthValue == 64, "Currently width is required to be 64")
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
      val registerBlock = addrBlockMap.getRegister
      registers.foreach { case(mname: String, _) =>
        val addr = module.csrs.module.addrmap(mname)
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
    addrBlocks.foreach(memoryMap.getMemoryMap.add(_))
    subspaceRefs.foreach(memoryMap.getMemoryMap.add(_))
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

  // Taken from iotesters
  private object getDataNames {
    def apply(data: Data): Seq[(Element, String)] = data match {
      case e: Element => Seq( (e, e.name) )
      case r: Record => r.elements.flatMap {
        case (name, elem) => elem match {
          case e: Element => Seq( (e, name) )
          case _ => getDataNames(elem).map { case (e, n) => (e, name + "_" + n)}
        }
      }.toSeq
    }
    /*def apply(name: String, data: Data): Seq[(Element, String)] = data match {
      case e: Element => Seq(e -> name)
      case b: Record => b.elements.toSeq flatMap {case (n, e) => apply(s"${name}_$n", e)}
      case v: Vec[_] => v.zipWithIndex flatMap {case (e, i) => apply(s"${name}_$i", e)}
    }
    def apply(dut: Module, separator: String = "."): Seq[(Element, String)] =
      apply(dut.io.pathName replace (".", separator), dut.io)*/
  }



  // direction: output = true, input = false
  def makeAXI4StreamPorts(prefix: String, bundle: AXI4StreamBundle): Seq[PortType] = {
    val ports = getDataNames(bundle).map { case(elem, name) =>
      (name, DataMirror.directionOf(elem) != ActualDirection.Output, elem.getWidth)
    }.filter { case (name, output, width) => width > 0 }

    ports.sorted.map { case (name, portdir, width) =>
      makePort(s"${prefix}_$name", portdir, width)
    }
  }

  def makeAXI4Ports(prefix: String, bundle: AXI4Bundle): Seq[PortType] = {
    val ports = getDataNames(bundle).map { case (elem, name) =>
      (name, DataMirror.directionOf(elem) != ActualDirection.Output, elem.getWidth)
    }.filter { case (name, output, width) => width > 0 }

    //val ports = Seq(
      //("ar_valid", direction, 1),
      //("ar_ready", !direction, 1)// ,
      // ("ar_bits_id", direction, config.nastiXIdBits),
      // ("ar_bits_addr", direction, config.nastiXAddrBits),
      // ("ar_bits_size", direction, config.nastiXSizeBits),
      // ("ar_bits_len", direction, config.nastiXLenBits),
      // ("ar_bits_burst", direction, config.nastiXBurstBits),
      // ("ar_bits_lock", direction, 1),
      // ("ar_bits_cache", direction, config.nastiXCacheBits),
      // ("ar_bits_prot", direction, config.nastiXProtBits),
      // ("ar_bits_qos", direction, config.nastiXQosBits),
      // ("ar_bits_region", direction, config.nastiXRegionBits),
      // ("ar_bits_user", direction, config.nastiXUserBits),
      // ("aw_valid", direction, 1),
      // ("aw_ready", !direction, 1),
      // ("aw_bits_id", direction, config.nastiXIdBits),
      // ("aw_bits_addr", direction, config.nastiXAddrBits),
      // ("aw_bits_size", direction, config.nastiXSizeBits),
      // ("aw_bits_len", direction, config.nastiXLenBits),
      // ("aw_bits_burst", direction, config.nastiXBurstBits),
      // ("aw_bits_lock", direction, 1),
      // ("aw_bits_cache", direction, config.nastiXCacheBits),
      // ("aw_bits_prot", direction, config.nastiXProtBits),
      // ("aw_bits_qos", direction, config.nastiXQosBits),
      // ("aw_bits_region", direction, config.nastiXRegionBits),
      // ("aw_bits_user", direction, config.nastiXUserBits),
      // ("w_valid", direction, 1),
      // ("w_ready", !direction, 1),
      // ("w_bits_data", direction, config.nastiXDataBits),
      // ("w_bits_strb", direction, config.nastiWStrobeBits),
      // ("w_bits_last", direction, 1),
      // ("w_bits_user", direction, config.nastiXUserBits),
      // ("r_valid", !direction, 1),
      // ("r_ready", direction, 1),
      // ("r_bits_id", !direction, config.nastiXIdBits),
      // ("r_bits_resp", !direction, config.nastiXRespBits),
      // ("r_bits_data", !direction, config.nastiXDataBits),
      // ("r_bits_last", !direction, 1),
      // ("r_bits_user", !direction, config.nastiXUserBits),
      // ("b_valid", !direction, 1),
      // ("b_ready", direction, 1),
      // ("b_bits_id", !direction, config.nastiXIdBits),
      // ("b_bits_resp", !direction, config.nastiXRespBits),
      // ("b_bits_user", !direction, config.nastiXUserBits)
    //)

    ports.sorted.map { case (name, portdir, width) =>
      makePort(s"${prefix}_$name", portdir, width)
    }
  }

  def makeClockAndResetPorts: Seq[PortType] = {
    val ports = Seq(
      ("clock", false, 1),
      ("reset", false, 1)
    )

    ports.sorted.map { case (name, portdir, width) =>
      makePort(s"$name", portdir, width)
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
    fileSets.getFileSet.add(fileSet)
    fileSets
  }

  //////////////////////////////////////////////
  //////////// Parameters //////////////////////
  //////////////////////////////////////////////

  def makeParameters(parameterMap: scala.collection.Map[String, String]): SpiritParameters = {
    val parameters = new SpiritParameters()
    for ( (name, value) <- parameterMap ) {
      val nameValuePairType = new NameValuePairType
      nameValuePairType.setName(name)
      val nameValuePairTypeValue = new NameValuePairType.Value
      nameValuePairTypeValue.setValue(value)
      nameValuePairType.setValue(nameValuePairTypeValue)
      parameters.getParameter.add(nameValuePairType)
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
