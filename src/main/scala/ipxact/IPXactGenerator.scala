package ipxact

import org.accellera.spirit.v1685_2009.{File => SpiritFile, Parameters => SpiritParameters, _}
import javax.xml.bind.{JAXBContext, Marshaller}
import java.io.{File, FileOutputStream}

import chisel3.experimental.FixedPoint
import chisel3.internal.firrtl.Circuit
import dspblocks._
import dsptools.numbers.DspComplex
import freechips.rocketchip.amba.axi4.{AXI4MasterNode, AXI4MasterParameters, AXI4MasterPortParameters}
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.coreplex.BaseCoreplexConfig
import freechips.rocketchip.diplomacy.{AddressSet, LazyModule}
import freechips.rocketchip.util.{GeneratorApp, ParsedInputNames}
import ofdm.{Autocorr, AutocorrParams}

import scala.collection.Seq

trait IPXactGeneratorApp {

  /////////////////////////////////////////////
  //////////// Generate ////////////////////////
  //////////////////////////////////////////////

  def verilogFilename: String
  def ipxactDir: String

  def generateIPXact(component: ComponentType): Unit = generateIPXact(Seq(component))
  def generateIPXact(components: Seq[ComponentType]): Unit = {

    val factory = new ObjectFactory

    components.foreach{ componentType => {
      componentType.setFileSets(IPXact.makeFileSets(verilogFilename))
      val component = factory.createComponent(componentType)
      // create name based off component parameters
      val ofPath = ipxactDir +
        componentType.getLibrary() + "_" +
        componentType.getName() + "_" +
        componentType.getVendor() + "_" +
        componentType.getVersion() + ".xml"
      val of = new File(ofPath)
      of.getParentFile().mkdirs()
      val fos = new FileOutputStream(of)
      val context = JAXBContext.newInstance(classOf[ComponentInstance])
      val marshaller = context.createMarshaller()
      marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
      marshaller.marshal(component, fos)
    }}
  }
}

object TestGenerator extends IPXactGeneratorApp with App {
  override val verilogFilename: String = "Passthrough.v"
  override val ipxactDir: String = "./"

  implicit val p: Parameters = Parameters.root((new BaseCoreplexConfig).toInstance)

  val passthroughparams = PassthroughParams(depth = 5)
  val blindNodes = DspBlockBlindNodes.apply(
    AXI4StreamBundleParameters(
      n = 8,
      i = 1,
      d = 1,
      u = 1,
      hasData = true,
      hasStrb = true,
      hasKeep = true
    ),
    () => AXI4MasterNode(Seq(AXI4MasterPortParameters(Seq(AXI4MasterParameters("passthrough")))))
  )

  val dut = () => {
    val lazyMod = LazyModule(DspBlock.blindWrapper(() => new AXI4Passthrough(passthroughparams), blindNodes))
    val m = lazyMod.module
    IPXactComponents._ipxactComponents += DspIPXact.makeDspBlockComponent(lazyMod.internal)
    m
  }


  chisel3.Driver.dumpFirrtl(chisel3.Driver.elaborate(dut), None)
  generateIPXact(IPXactComponents.ipxactComponents())
}