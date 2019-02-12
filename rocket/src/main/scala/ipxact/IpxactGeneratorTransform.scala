// See LICENSE for license details.

package ipxact

import java.io.{File, PrintWriter}

import firrtl.annotations.NoTargetAnnotation
import firrtl.{CircuitForm, CircuitState, Driver, ExecutionOptionsManager}
import firrtl.{HasFirrtlOptions, HighForm, LowForm, Parser, Transform}
import freechips.rocketchip.diplomacy.AddressMapEntry
import freechips.rocketchip.util.{AddressMapAnnotation, ParamsAnnotation, RegFieldDescMappingAnnotation}

//class IpxactGeneratorAnnotation(target: InstanceId, transform: Transform, value: String) extends Annotation {
//  override def update(renames: RenameMap): Seq[Annotation] = ???
//}

/**
  * This annotation carries the generated IP-XACT xml.
  * @param xmlDocument the document
  */
case class GeneratedIpxactAnnotation(xmlDocument: IpxactXmlDocument) extends NoTargetAnnotation

/**
  * This is a utility class that collects the xml as it is being assembled
  * @param vendor   creator of the content
  * @param library  software being used
  * @param name     circuit name
  * @param version  generator version
  */
class IpxactXmlDocument(vendor: String, library: String, name: String, version: String) {

  var components: Seq[scala.xml.Node] = Seq.empty

  def addComponents(newComponents:  Seq[scala.xml.Node]): Unit = {
    components = components ++ newComponents
  }

  def getXml: scala.xml.Node = {
    <spirit:component xmlns:spirit="http://www.spiritconsortium.org/XMLSchema/SPIRIT/1685-2009">
      <spirit:vendor>edu.berkeley.cs</spirit:vendor>
      <spirit:library>craft</spirit:library>
      <spirit:name>craft_radar_fir1</spirit:name>
      <spirit:version>1.0</spirit:version>
      {
      components
      }
    </spirit:component>
  }
}

/**
  * This transform builds a xml object from the annotations
  * and firrtl circuit
  */
class IpxactGeneratorTransform extends Transform {
  override def inputForm: CircuitForm = LowForm

  override def outputForm: CircuitForm = LowForm

  /**
    * Adds parameters to the parameters component
    * @param annotation the firrtl annotation for parameters
    * @return
    */
  def parameterAnnotationsToXml(annotation: ParamsAnnotation): Seq[scala.xml.Node] = {
    val prefix = annotation.paramsClassName
    annotation.params.map { case (paramName, value) =>
      <spirit:parameter>
        <spirit:name>{prefix + "." + paramName}</spirit:name>
        <spirit:value>{value}</spirit:value>
      </spirit:parameter>
    }.toSeq
  }

  /**
    * construct the address section from the annotation
    * @param annotation  address map information
    * @return
    */
  def addressAnnotationsToXml(annotation: AddressMapAnnotation): Seq[scala.xml.Node] = {
    annotation.mapping.map { case AddressMapEntry(range, permissions, names) =>
      ???
    }
  }

  /**
    * builds the register map stuff from the given annotation
    * @param annotation reg field information
    * @return
    */
  def regFieldDescMappingAnnotationsToXml(annotation: RegFieldDescMappingAnnotation): Seq[scala.xml.Node] = {
    <spirit:memoryMap>
      <spirit:name>{annotation.target}</spirit:name>
      <spirit:addressBlock>
        <spirit:name>{annotation.regMappingSer.displayName}</spirit:name>
        <spirit:baseAddress>{annotation.regMappingSer.baseAddress}</spirit:baseAddress>
        <spirit:range>{annotation.regMappingSer.regFields.length}</spirit:range>
        <spirit:width>{64}</spirit:width>
          {
            annotation.regMappingSer.regFields.map { regField =>
              <spirit:register>
                <spirit:name>{regField.name}</spirit:name>
                <spirit:addressOffset>{regField.byteOffset}</spirit:addressOffset>
                <spirit:size>{regField.bitWidth}</spirit:size>
              </spirit:register>
          }
        }
      </spirit:addressBlock>
    </spirit:memoryMap>
  }

  override protected def execute(state: CircuitState): CircuitState = {

    val xmlDocument = new IpxactXmlDocument(
      vendor = "edu.berkeley.cs",
      library = "rocket-chip",
      name = state.circuit.main,
      version = "0.1"
    )

    val paramsXml = <spirit:parameters>
      {
        state.annotations.flatMap {
          case a: ParamsAnnotation =>
            parameterAnnotationsToXml(a)
          case _ =>
            Seq.empty
        }
      }
    </spirit:parameters>

    xmlDocument.addComponents(paramsXml)

    val memoryMaps =
      <spirit:memoryMaps>
      {
      state.annotations.flatMap {
        case a: RegFieldDescMappingAnnotation =>
          regFieldDescMappingAnnotationsToXml(a)
        case _ =>
          Seq.empty
      }
      }
      </spirit:memoryMaps>

    xmlDocument.addComponents(memoryMaps)

    state.copy(annotations = state.annotations :+ GeneratedIpxactAnnotation(xmlDocument))
  }
}

object IpxactGeneratorTransform {
  val lineWidth: Int = 300
  val indent: Int    =   2

  def main(args: Array[String]): Unit = {

    val fileName = "AXI4GCD.fir"

    val optionsManager = new ExecutionOptionsManager("ipxact") with HasFirrtlOptions {
      commonOptions = commonOptions.copy(targetDirName = "./")
      firrtlOptions = firrtlOptions.copy(annotationFileNames = List("AXI4GCD.anno.json"))
    }

    val firrtlSource = io.Source.fromFile(fileName).getLines()

    val firrtl = Parser.parse(firrtlSource)

    val annotations = Driver.getAnnotations(optionsManager)

    val initialCircuitState = CircuitState(firrtl, HighForm, annotations)

    val generator = new IpxactGeneratorTransform

    val finalState = generator.execute(initialCircuitState)

    finalState.annotations.collectFirst { case g: GeneratedIpxactAnnotation => g } match {
      case Some(GeneratedIpxactAnnotation(xmlDocument)) =>
        val pp = new scala.xml.PrettyPrinter(lineWidth, indent)
        val sb = new StringBuilder
        sb ++= """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" + "\n"
        pp.format(xmlDocument.getXml, sb)
        println(sb.toString)

        val writer = new PrintWriter(new File(s"${finalState.circuit.main}.ipxact.xml"))
        writer.write(sb.toString)
        writer.close()

      case _ =>
        println(s"Something went wrong, no ipxact generated")
    }
  }
}