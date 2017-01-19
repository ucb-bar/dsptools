package sam

import util.GeneratorApp
import org.accellera.spirit.v1685_2009.{File => SpiritFile, Parameters => SpiritParameters, _}
import javax.xml.bind.{JAXBContext, Marshaller}
import java.io.{File, FileOutputStream}
import scala.collection.JavaConverters
import java.util.Collection
import java.math.BigInteger
import rocketchip._
import junctions._
import cde.Parameters
import dspjunctions._
import dspblocks._
import dsptools._

// includes IPXact generation
trait DspGeneratorApp extends GeneratorApp {

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

  def makeAXIPortMaps(prefix: String): BusInterfaceType.PortMaps = {
    makePortMaps(Seq(
      "ACLK"     -> "clock",
      "ARESETn"  -> "reset",
      "TVALID"   -> s"${prefix}_valid",
      "TLAST"    -> s"${prefix}_sync",
      "TDATA"    -> s"${prefix}_bits"))
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

  def makeAXIPorts(prefix: String, direction: Boolean, bits: Int): Seq[PortType] = {
    val ports = Seq(
      ("valid", direction, 1),
      ("sync", direction, 1),
      ("bits", direction, bits))

    ports.sorted.map { case (name, portdir, width) =>
      makePort(s"${prefix}_${name}", portdir, width) }
  }

  def makeAllPorts(bits_in: Int, bits_out: Int): ModelType.Ports = {
    val inPorts = makeAXIPorts(s"io_in", false, bits_in)
    val outPorts = makeAXIPorts(s"io_out", true, bits_out)
    val globalPorts = Seq(
      makePort("clock", false, 1),
      makePort("reset", false, 1))
    val ports = new ModelType.Ports
    ports.getPort().addAll(toCollection(globalPorts ++ inPorts ++ outPorts))
    ports
  }

  def makeInputInterface: BusInterfaceType = {
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

    val portMaps = makeAXIPortMaps(s"io_in")

    val busif = new BusInterfaceType
    busif.setName(s"io_in")
    busif.setBusType(busType)
    busif.setAbstractionType(abstractionType)
    busif.setPortMaps(portMaps)
    busif
  }

  def makeOutputInterface: BusInterfaceType = {
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

    val portMaps = makeAXIPortMaps(s"io_out")

    val busif = new BusInterfaceType
    busif.setName(s"io_out")
    busif.setBusType(busType)
    busif.setAbstractionType(abstractionType)
    busif.setPortMaps(portMaps)
    busif
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

  def makeParameters(factory: ObjectFactory): SpiritParameters = {
    val parameters = new SpiritParameters()
    val config = new DspConfig()
    for ( (name, value) <- config.getIPXACTParameters) {
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

  def generateIPXact {
    val bits_in = params(DspBlockKey).inputWidth
    val bits_out = params(DspBlockKey).outputWidth
    val factory = new ObjectFactory

    val busInterfaces = new BusInterfaces
    busInterfaces.getBusInterface().addAll(toCollection(Seq(makeInputInterface, makeOutputInterface)))

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
    model.setPorts(makeAllPorts(bits_in, bits_out))

    val componentType = new ComponentType
    componentType.setLibrary("ucb-art")
    componentType.setName("CraftDSPModule")
    componentType.setVendor("edu.berkeley.cs")
    componentType.setVersion("1.0")
    componentType.setBusInterfaces(busInterfaces)
    componentType.setModel(model)
    componentType.setFileSets(makeFileSets(factory))
    componentType.setParameters(makeParameters(factory))

    val component = factory.createComponent(componentType)

    val of = new File(td, s"$longName.xml")
    of.getParentFile().mkdirs()
    val fos = new FileOutputStream(of)
    val context = JAXBContext.newInstance(classOf[ComponentInstance])
    val marshaller = context.createMarshaller()
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
    marshaller.marshal(component, fos)
  }
}

object Generator extends DspGeneratorApp {
  val longName = names.fullTopModuleClass + "." + names.configs
  generateFirrtl
  generateIPXact
}
