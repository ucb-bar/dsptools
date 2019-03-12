// See LICENSE for license details.

package ipxact

import java.io.{File, PrintWriter}

import firrtl.annotations.{Annotation, ModuleName, ModuleTarget, NoTargetAnnotation}
import firrtl.ir._
import firrtl.{CircuitForm, CircuitState, Driver, ExecutionOptionsManager}
import firrtl.{HasFirrtlOptions, HighForm, LowForm, Parser, Transform, _}
import freechips.rocketchip.diplomacy.AddressMapEntry
import freechips.rocketchip.util.{AddressMapAnnotation, ParamsAnnotation, RegFieldDescMappingAnnotation}
import ipxact.IpxactGeneratorTransform.{indent, lineWidth}

/**
  * This annotation carries the generated IP-XACT xml.
  * @param xmlDocument the document
  */
//TODO chick: Is there any merit in passing this down the compiler stack
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
      <spirit:vendor>{vendor}</spirit:vendor>
      <spirit:library>{library}</spirit:library>
      <spirit:name>{name}</spirit:name>
      <spirit:version>{version}</spirit:version>
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
  def regFieldDescMappingAnnotationsToXml(name: String, annotation: RegFieldDescMappingAnnotation): Seq[Any] = {
    <spirit:memoryMap>
      <spirit:name>{s"${name}_mm"}</spirit:name>
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

  //TODO chick: Should this module be module specific
  def generateModel(state: CircuitState): Seq[scala.xml.Node] = {
    <spirit:model>
      <spirit:views>
        <spirit:view>
          <spirit:name>RTL</spirit:name>
          <spirit:envIdentifier>::</spirit:envIdentifier>
          <spirit:fileSetRef>
            <spirit:localName>hdlSource</spirit:localName>
          </spirit:fileSetRef>
        </spirit:view>
      </spirit:views>
        {
        getPorts(state)
        }
    </spirit:model>
  }

  def directionToIpxact(direction: Direction): String = {
    direction match {
      case Input => "in"
      case Output => "out"
      case _ => "unknown"
    }
  }

  /**
    * Walk through all ports of a circuit
    * @param state the circuit state
    */
  //scalastyle:off method.length
  def getPorts(state: CircuitState): Seq[scala.xml.Node] = {

    def walkFields(field: Field, name: String, direction: Direction, depth: Int = 0): Seq[scala.xml.Node] = {

      val newDirection = Utils.times(direction, field.flip)

      field.tpe match {
        case b: BundleType =>
          b.fields.flatMap { field =>
            walkFields(field, name + "_" + field.name, newDirection, depth + 1)
          }
        case _ =>
          <spirit:port>
            <spirit:name>{name}</spirit:name>
            <spirit:wire>
              <spirit:direction>{directionToIpxact(newDirection)}</spirit:direction>
              {
              if (bitWidth(field.tpe) > 1) {
                <spirit:vector>
                  <spirit:left>{bitWidth(field.tpe) - 1}</spirit:left>
                  <spirit:right>0</spirit:right>
                </spirit:vector>
              }
              }
            </spirit:wire>
          </spirit:port>
      }
    }

    <spirit:ports>
      {
        state.circuit.modules.flatMap { module =>
        module.ports.map { port =>
          port.tpe match {
            case b: BundleType =>
              b.fields.flatMap { field =>

                //TODO (chick) Ask Paul what new direction is for here.

                val newDirection = Utils.times(port.direction, field.flip)
                walkFields(field, port.name + "_" + field.name, port.direction)
              }
            case _ =>
              <spirit:port>
                <spirit:name>{port.name}</spirit:name>
                <spirit:wire>
                  <spirit:direction>{directionToIpxact(port.direction)}</spirit:direction>
                  {
                    if (bitWidth(port.tpe) > 1) {
                      <spirit:vector>
                        <spirit:left>{bitWidth(port.tpe) - 1}</spirit:left>
                        <spirit:right>0</spirit:right>
                      </spirit:vector>
                    }
                  }
                </spirit:wire>
              </spirit:port>
          }

        }
      }
      }
    </spirit:ports>
  }

  def generateBusInterface(annotation: ParamsAnnotation): Option[scala.xml.Node] = {
    annotation.paramsClassName match {
      case "AXI4BundleParameters" =>
        annotation.target match {
          case ModuleName(name, circuit) =>
            Some(
              <spirit:busInterface>
                <spirit:name>{name}</spirit:name>
                <spirit:busType spirit:vendor="amba.com"
                                spirit:library="AMBA4" spirit:name="AXI4Stream" spirit:version="r0p0_1"/>
                <spirit:abstractionType spirit:vendor="amba.com" spirit:library="AMBA4"
                                        spirit:name="AXI4Stream_rtl" spirit:version="r0p0_1"/>
                <spirit:slave/>
                <spirit:portMaps>
                </spirit:portMaps>
              </spirit:busInterface>
            )
          case _ =>
            None
        }
      case _ =>
        None
    }
  }

  def writeXml(dir: String, moduleName: String, xmlDocument: IpxactXmlDocument): Unit = {
    val xmlFile = new File(dir + File.separator + s"$moduleName.ipxact.xml")

    val pp = new scala.xml.PrettyPrinter(lineWidth, indent)
    val sb = new StringBuilder
    sb ++= """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" + "\n"
    pp.format(xmlDocument.getXml, sb)

    val writer = new PrintWriter(xmlFile)
    writer.write(sb.toString)
    writer.close()
  }

  //scalastyle:off cyclomatic.complexity
  override protected def execute(state: CircuitState): CircuitState = {
    val ipxactModules = state.annotations.collect {
      case a: IpxactModuleAnnotation => a.target.complete
    }

    val targetDir = state.annotations.collectFirst {
      case t: firrtl.stage.TargetDirAnnotation => t.targetDirName
    }.getOrElse("./")

    def doOneModule(moduleName: String, filteredAnnotations: AnnotationSeq): Annotation = {

      //TODO chick: what should the name be here
      val xmlDocument = new IpxactXmlDocument(
        vendor = "edu.berkeley.cs",
        library = "rocket-chip",
//        name = state.circuit.main,
        name = moduleName,
        version = "0.1"
      )

      val busInterfaceXml = <spirit:busInterfaces>
        {
        filteredAnnotations.flatMap {
          case a: ParamsAnnotation =>
            generateBusInterface(a)
          case _ =>
            None

        }
        }
      </spirit:busInterfaces>

      xmlDocument.addComponents(busInterfaceXml)

      val paramsXml = <spirit:parameters>
        {
        filteredAnnotations.flatMap {
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
          filteredAnnotations.flatMap {
            case a: RegFieldDescMappingAnnotation =>
              regFieldDescMappingAnnotationsToXml(state.circuit.main, a)
            case _ =>
              Seq.empty
          }
          }
        </spirit:memoryMaps>

      xmlDocument.addComponents(memoryMaps)

      xmlDocument.addComponents(generateModel(state))

      writeXml(targetDir, moduleName, xmlDocument)
      GeneratedIpxactAnnotation(xmlDocument)
    }

    val ipxactXmlAnnotations = ipxactModules.map { moduleTarget =>
      val moduleNameString = moduleTarget match {
        case m: ModuleTarget => s"${m.circuit}.${m.module}"
      }

      val annosForThisModule = state.annotations.flatMap {
        case a: ParamsAnnotation if a.target.toTarget.serialize.startsWith(moduleNameString) =>
          Some(a)
        case _ =>
          None
      }


      doOneModule(moduleNameString, annosForThisModule)
    }


    state.copy(annotations = state.annotations ++ ipxactXmlAnnotations)
  }
}

object IpxactGeneratorTransform {
  val lineWidth: Int = 300
  val indent: Int    =   2

  def main(args: Array[String]): Unit = {

    val optionsManager = new ExecutionOptionsManager("ipxact") with HasFirrtlOptions {
      commonOptions = commonOptions.copy(targetDirName = "test_run_dir/axi4gcd", topName = "axi4gcd")
      firrtlOptions = firrtlOptions.copy(annotationFileNames = List("AXI4GCD.anno.json"))
    }

    val fileName = optionsManager.getBuildFileName("fir")

    val firrtlSource = io.Source.fromFile(fileName).getLines()

    val firrtl = Parser.parse(firrtlSource)

    val annotations = Driver.getAnnotations(optionsManager)

    val initialCircuitState = CircuitState(firrtl, HighForm, annotations)

    val generator = new IpxactGeneratorTransform

    val finalState = generator.execute(initialCircuitState)

    finalState.annotations.collectFirst { case a: GeneratedIpxactAnnotation => }.getOrElse(
      throw new Exception("oops some xml should have been generated")
    )
  }
}