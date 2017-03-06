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
import ipxact._

object Generator extends GeneratorApp with IPXactGeneratorApp  {
  val longName = names.fullTopModuleClass + "." + names.configs
  def verilogFilename = s"${longName}.top.v"
  def ipxactDir = td
  generateFirrtl
  generateIPXact(IPXactComponents.ipxactComponents())
}
