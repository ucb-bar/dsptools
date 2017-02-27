package dspblocks

import util.GeneratorApp
import org.accellera.spirit.v1685_2009.{File => SpiritFile, Parameters => SpiritParameters, _}
import javax.xml.bind.{JAXBContext, Marshaller}
import java.io.{File, FileOutputStream}
import scala.collection.JavaConverters
import java.util.Collection
import uncore.tilelink._
import java.math.BigInteger
import rocketchip._
import junctions._
import dspjunctions._
import ipxact._
import cde._

class NastiConfig(implicit val p: Parameters) extends HasNastiParameters {}

case class IPXactParameters(id: String) extends Field[Map[String, String]]

object IPXactComponents {
  //private[dspblocks] val _ipxactComponents: scala.collection.mutable.ArrayBuffer[ComponentType] = 
  val _ipxactComponents: scala.collection.mutable.ArrayBuffer[ComponentType] = 
    scala.collection.mutable.ArrayBuffer.empty[ComponentType]
  def ipxactComponents(): Seq[ComponentType] = _ipxactComponents
}

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
    val ctrlAXIInterface = makeAXI4Interface(ctrl_mmref, "io_axi", "axi4_slave", true)
    val dataAXIInterface = makeAXI4Interface(data_mmref, "io_axi_out", "axi4_data_slave", false)

    val busInterfaces = new BusInterfaces
    busInterfaces.getBusInterface().addAll(toCollection(Seq(streamInInterface, ctrlAXIInterface, dataAXIInterface)))
    busInterfaces
  }

  def makeXbarInterfaces(inputs: Int, outputs: Int, mmref: String, asref: String): BusInterfaces = {
    val inputInterfaces = (0 until inputs).map(i => makeAXI4Interface(s"${mmref}_${i}", s"io_in_${i}", s"xbar_in_${i}", false, outputs, "io_out"))
    val outputInterfaces = (0 until outputs).map(i => makeAXI4Interface(s"${asref}_${i}", s"io_out_${i}", s"xbar_out_${i}", true))
    
    val busInterfaces = new BusInterfaces
    busInterfaces.getBusInterface().addAll(toCollection(Seq(inputInterfaces, outputInterfaces).flatten))
    busInterfaces
  }


  //////////////////////////////////////////////
  //////////// Memory Maps /////////////////////
  //////////////////////////////////////////////

  // TODO: should registers be AddrMap?
  def makeDspBlockMemoryMaps(mmref: String, baseAddress: BigInt, registers: Seq[String]): MemoryMaps = {
    val addrBlock = makeAddressBlock("SCRFile", baseAddress, registers)
    val memoryMaps = new MemoryMaps
    memoryMaps.getMemoryMap().add(makeMemoryMap(mmref, addrBlocks=Seq(addrBlock)))
    memoryMaps
  }

  // TODO: make the data map correct
  def makeSAMMemoryMaps(ctrl_mmref: String, data_mmref: String, ctrl_baseAddress: BigInt, data_baseAddress: BigInt, registers: Seq[String]): MemoryMaps = {
    val ctrl_addrBlock = makeAddressBlock("SCRFile", ctrl_baseAddress, registers)
    val data_addrBlock = makeAddressBlock("SAMData", data_baseAddress, Seq("SAM"))
    val memoryMaps = new MemoryMaps
    memoryMaps.getMemoryMap().addAll(toCollection(Seq(makeMemoryMap(ctrl_mmref, addrBlocks=Seq(ctrl_addrBlock)), makeMemoryMap(data_mmref, addrBlocks=Seq(data_addrBlock)))))
    memoryMaps
  }

  def makeXbarMemoryMaps(ref: String, inputs: Int, addrMap: AddrMap): MemoryMaps = {
    val signalMaps = addrMap.flatten.zipWithIndex.map { case (entry, i) =>
      (s"io_out_${i}", entry.region.start)
    }
    val memoryMaps = new MemoryMaps
    memoryMaps.getMemoryMap().addAll(toCollection(
      (0 until inputs).map(i => makeMemoryMap(ref, subspaceRefs=signalMaps.map{ case(name, baseAddr) => makeSubspaceRef(s"subspacemap_${ref}_${name}_${i}", baseAddr, name)}))
    ))
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

  def makeXbarAddressSpaces(ref: String, addrMap: AddrMap): AddressSpaces = {
    val addressSpaces = new AddressSpaces
    addressSpaces.getAddressSpace.addAll(toCollection(
      addrMap.flatten.zipWithIndex.map { case (entry, i) =>
        makeAddressSpace(s"${ref}_${i}", entry.region.size)
      }
    ))
    addressSpaces
  }


  //////////////////////////////////////////////
  //////////// Model ///////////////////////////
  //////////////////////////////////////////////

  // assumes DSP block, stream in and out and AXI4 control
  def makeDspBlockPorts(bits_in: Int, bits_out: Int)(implicit p: Parameters): ModelType.Ports = {
    val config = new NastiConfig
    val streamInPorts = makeAXI4StreamPorts("io_in", false, bits_in)
    val streamOutPorts = makeAXI4StreamPorts("io_out", true, bits_out)
    val axiPorts = makeAXI4Ports(s"io_axi", false, config)
    val globalPorts = makeClockAndResetPorts

    val ports = new ModelType.Ports
    ports.getPort().addAll(toCollection(globalPorts ++ streamInPorts ++ streamOutPorts ++ axiPorts))
    ports
  }

  // assumes SAM block, stream in and AXI4 control and data out
  def makeSAMPorts(bits_in: Int)(implicit p: Parameters): ModelType.Ports = {
    // TODO: do we need to separate control from data AXI interface configs here?
    val config = new NastiConfig
    val streamInPorts = makeAXI4StreamPorts(s"io_in", false, bits_in)
    val ctrlAXIPorts = makeAXI4Ports(s"io_axi", false, config)
    val dataAXIPorts = makeAXI4Ports(s"io_axi_out", false, config)
    val globalPorts = makeClockAndResetPorts

    val ports = new ModelType.Ports
    ports.getPort().addAll(toCollection(globalPorts ++ streamInPorts ++ ctrlAXIPorts ++ dataAXIPorts))
    ports
  }

  def makeXbarPorts(inputs: Int, outputs: Int)(implicit p: Parameters): ModelType.Ports = {
    val config = new NastiConfig
    val inPorts = (0 until inputs).map(i => makeAXI4Ports(s"io_in_${i}", false, config))
    val outPorts = (0 until outputs).map(i => makeAXI4Ports(s"io_out_${i}", true, config))
    val globalPorts = makeClockAndResetPorts

    val ports = new ModelType.Ports
    ports.getPort().addAll(toCollection(globalPorts ++ (inPorts ++ outPorts).flatten))
    ports
  }

  //////////////////////////////////////////////
  //////////// Component ///////////////////////
  //////////////////////////////////////////////

  def makeDspBlockComponent(_baseAddress: BigInt)(implicit p: Parameters): ComponentType = {
    val name = p(DspBlockId)
    val mmref = s"${name}_mm"
    val gk = try {
      Some(p(GenKey(p(DspBlockId))))
    } catch  {
      case e: ParameterUndefinedException => None
    }
    val dbk = try {
      Some(p(DspBlockKey(p(DspBlockId))))
    } catch {
      case e: ParameterUndefinedException => None
    }
    val (bits_in, bits_out) = (gk, dbk) match {
      case (Some(g), None) => (g.genIn.getWidth * g.lanesIn, g.genOut.getWidth * g.lanesOut)
      case (None, Some(d)) => (d.inputWidth, d.outputWidth)
      case _ => throw dsptools.DspException("Input and output widths could not be found in the Parameters object!")
    }
//   val bits_in = gk.genIn.getWidth * gk.lanesIn
//   val bits_out = gk.genOut.getWidth * gk.lanesOut
    val baseAddress = _baseAddress
    val registers = testchipip.SCRAddressMap.contents.head._2.map{ case (scrName, scrOffset) => scrName }.toArray
    val busInterfaces = makeDspBlockInterfaces(mmref) 
    val addressSpaces = makeDspBlockAddressSpaces
    val memoryMaps = makeDspBlockMemoryMaps(mmref, baseAddress, registers)
    val model = makeModel(makeDspBlockPorts(bits_in, bits_out))
    val parameters = makeParameters(p(IPXactParameters(p(DspBlockId))))
    makeComponent(name, busInterfaces, addressSpaces, memoryMaps, model, parameters)
  }

  def makeSAMComponent(_ctrl_baseAddress: BigInt, _data_baseAddress: BigInt)(implicit p: Parameters): ComponentType = {
    val name = p(DspBlockId)
    val ctrl_mmref = s"${name}_ctrl_mm"
    val data_mmref = s"${name}_data_mm"
    val dbk = p(DspBlockKey(p(DspBlockId)))
    val bits_in = dbk.inputWidth
    val ctrl_baseAddress = _ctrl_baseAddress
    val data_baseAddress = _data_baseAddress
    val registers = testchipip.SCRAddressMap.contents.head._2.map{ case (scrName, scrOffset) => scrName }.toArray
    val busInterfaces = makeSAMInterfaces(ctrl_mmref, data_mmref) 
    val addressSpaces = makeSAMAddressSpaces
    val memoryMaps = makeSAMMemoryMaps(ctrl_mmref, data_mmref, ctrl_baseAddress, data_baseAddress, registers)
    val model = makeModel(makeSAMPorts(bits_in))
    val parameters = makeParameters(p(IPXactParameters(p(DspBlockId))))
    makeComponent(name, busInterfaces, addressSpaces, memoryMaps, model, parameters)
  }

  def makeXbarComponent(implicit p: Parameters): ComponentType = {
    val name = p(DspBlockId)
    val mmref = s"${name}_mm"
    val asref = s"${name}_as"
    val addrMap = p(GlobalAddrMap)
    val inputs = p(InPorts)
    val outputs = p(OutPorts)
    // TODO
    val ctrl_baseAddress = 0
    val data_baseAddress = 0
    val busInterfaces = makeXbarInterfaces(inputs, outputs, mmref, asref) 
    val addressSpaces = makeXbarAddressSpaces(asref, addrMap)
    val memoryMaps = makeXbarMemoryMaps(mmref, inputs, addrMap)
    val model = makeModel(makeXbarPorts(inputs, outputs))
    val parameters = makeParameters(scala.collection.mutable.HashMap[String, String]())
    makeComponent(name, busInterfaces, addressSpaces, memoryMaps, model, parameters)
  }
}

object DspIPXact extends HasDspIPXact
