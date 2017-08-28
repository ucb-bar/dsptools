package craft

/*
import util.GeneratorApp
import org.accellera.spirit.v1685_2009.{File => SpiritFile, Parameters => SpiritParameters, _}
import javax.xml.bind.{JAXBContext, Marshaller}
import java.io._
import _root_.firrtl.annotations.AnnotationYamlProtocol._
import scala.collection.JavaConverters
import net.jcazevedo.moultingyaml._
import java.util.Collection
import java.math.BigInteger
import rocketchip._
import junctions._
import cde._
import dspjunctions._
import dspblocks._
import dsptools._
import ipxact._
import scala.collection.mutable.LinkedHashSet
import rocket.{XLen, UseVM, UseAtomics, UseCompressed, FPUKey}

object Generator extends GeneratorApp with IPXactGeneratorApp  {
  val longName = names.fullTopModuleClass + "." + names.configs
  def verilogFilename = s"${longName}.v"
  def ipxactDir = td
  generateFirrtl
  generateIPXact(IPXactComponents.ipxactComponents())

  // need to override so we can dump annotations file
  override def generateFirrtl {
    chisel3.Driver.dumpFirrtl(circuit, Some(new File(td, s"$longName.fir"))) // FIRRTL
    
    // also dump annotations file
    val annotationFile = new File(td, s"$longName.anno")
    val af = new FileWriter(annotationFile)
    af.write(circuit.annotations.toArray.toYaml.prettyPrint)
    af.close()
  }

  /** Output a global address map */
  def generateAddressMap {
    AddrMapStringOutput.contents.foreach(c => writeOutputFile(td, s"${names.configs}.addrmap", c))
  }

  // [stevo]: copied from rocketchip
  val rv64RegrTestNames = LinkedHashSet(
        "rv64ud-v-fcvt",
        "rv64ud-p-fdiv",
        "rv64ud-v-fadd",
        "rv64uf-v-fadd",
        "rv64um-v-mul",
        "rv64mi-p-breakpoint",
        "rv64uc-v-rvc",
        "rv64ud-v-structural",
        "rv64si-p-wfi",
        "rv64um-v-divw",
        "rv64ua-v-lrsc",
        "rv64ui-v-fence_i",
        "rv64ud-v-fcvt_w",
        "rv64uf-v-fmin",
        "rv64ui-v-sb",
        "rv64ua-v-amomax_d",
        "rv64ud-v-move",
        "rv64ud-v-fclass",
        "rv64ua-v-amoand_d",
        "rv64ua-v-amoxor_d",
        "rv64si-p-sbreak",
        "rv64ud-v-fmadd",
        "rv64uf-v-ldst",
        "rv64um-v-mulh",
        "rv64si-p-dirty")

  val rv32RegrTestNames = LinkedHashSet(
      "rv32mi-p-ma_addr",
      "rv32mi-p-csr",
      "rv32ui-p-sh",
      "rv32ui-p-lh",
      "rv32uc-p-rvc",
      "rv32mi-p-sbreak",
      "rv32ui-p-sll")

  override def addTestSuites {
    import DefaultTestSuites._
    val xlen = params(XLen)
    val vm = params(UseVM)
    val env = if (vm) List("p","v") else List("p")
    params(FPUKey) foreach { case cfg =>
      if (xlen == 32) {
        TestGeneration.addSuites(env.map(rv32ufNoDiv))
      } else {
        TestGeneration.addSuite(rv32udBenchmarks)
        TestGeneration.addSuites(env.map(rv64ufNoDiv))
        TestGeneration.addSuites(env.map(rv64udNoDiv))
        if (cfg.divSqrt) {
          TestGeneration.addSuites(env.map(rv64uf))
          TestGeneration.addSuites(env.map(rv64ud))
        }
      }
    }
    if (params(UseAtomics))    TestGeneration.addSuites(env.map(if (xlen == 64) rv64ua else rv32ua))
    if (params(UseCompressed)) TestGeneration.addSuites(env.map(if (xlen == 64) rv64uc else rv32uc))
    val (rvi, rvu) =
      if (xlen == 64) ((if (vm) rv64i else rv64pi), rv64u)
      else            ((if (vm) rv32i else rv32pi), rv32u)

    TestGeneration.addSuites(rvi.map(_("p")))
    TestGeneration.addSuites((if (vm) List("v") else List()).flatMap(env => rvu.map(_(env))))
    TestGeneration.addSuite(benchmarks)
    TestGeneration.addSuite(new RegressionTestSuite(if (xlen == 64) rv64RegrTestNames else rv32RegrTestNames))
  }
  if (! (longName contains "DspTop")) {
    generateTestSuiteMakefrags
    generateParameterDump
    generateConfigString
    generateAddressMap
  }
}

object AddrMapStringOutput {
  var contents: Option[String] = None
}
*/
