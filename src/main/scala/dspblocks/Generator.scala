package dspblocks

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

trait DspGeneratorApp extends GeneratorApp with IPXactGeneratorApp

object DspGenerator extends DspGeneratorApp {
  val longName = names.fullTopModuleClass + "." + names.configs
  generateFirrtl
  //generateTestSuiteMakefrags // TODO: Needed only for legacy make targets
  //generateParameterDump      // TODO: Needed only for legacy make targets
  generateIPXact
}
