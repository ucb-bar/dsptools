// See LICENSE for license details.

package ipxact

import java.io.{File, PrintWriter}

import firrtl.annotations._
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
//TODO: (chick) Is there any merit in passing this down the compiler stack
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
    <spirit:component xmlns:spirit="http://www.spiritconsortium.org/XMLSchema/SPIRIT/1685-2009"
                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:schemaLocation="http://www.spiritconsortium.org/XMLSchema/SPIRIT/1685-2009">
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

case class IpxactBundleName(moduleName: ModuleTarget, bundleName: String) extends Annotation {
  override def update(renames: RenameMap): Seq[Annotation] = Seq.empty
}

class IpxactBundleCaptureTransform extends Transform {
  override def inputForm: CircuitForm = HighForm

  override def outputForm: CircuitForm = HighForm

  override def execute(state: CircuitState): CircuitState = {
//    state.copy(annotations = updatedAnnotations)
    state
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
  def generateParameter(annotation: ParamsAnnotation): Seq[scala.xml.Node] = {
    val prefix = annotation.paramsClassName
    annotation.params.map { case (paramName, value) =>
      <spirit:parameter>
        <spirit:name>{prefix + "." + paramName}</spirit:name>
        <spirit:value>{value}</spirit:value>
      </spirit:parameter>
    }.toSeq
  }

  def generateParameters(annotationSeq: AnnotationSeq): Option[scala.xml.Node] = {
    val parametersXml = annotationSeq.flatMap {
      case a: ParamsAnnotation => generateParameter(a)
      case _ =>
        Seq.empty
    }
    if(parametersXml.isEmpty) {
      None
    }
    else {
      Some(
        <spirit:parameters>
          parametersXml
        </spirit:parameters>
      )
    }
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

  //TODO: (chick) Should this module be module specific
  def generateModel(state: CircuitState, moduleName: String): Seq[scala.xml.Node] = {
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
          getPorts(state, moduleName)
        }
    </spirit:model>
  }

  /**
    * Describes where the associated verilog source resides
    * @param moduleName
    * @return
    */
  //TODO: (chick) how does this related to one-file per module and the name of the verilog output in general
  // can we just point to all .v files for each module
  def generateFileSets(moduleName: String): scala.xml.Node = {
    <spirit:fileSets>
      <spirit:fileSet>
        <spirit:name>hdlSource</spirit:name>
        <spirit:file>
          <spirit:name>{moduleName}.v</spirit:name>
          <spirit:fileType>verilogSource</spirit:fileType>
        </spirit:file>
      </spirit:fileSet>
    </spirit:fileSets>
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
  def getPorts(state: CircuitState, moduleName: String): Seq[scala.xml.Node] = {

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
        state.circuit.modules.find { module => module.name == moduleName } match {
          case Some(module) =>
            module.ports.map { port =>
              port.tpe match {
                case b: BundleType =>
                  b.fields.flatMap { field =>
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
          case None =>
        }
      }
    </spirit:ports>
  }

  def generatePortMaps(portInfo: Map[String, Any]): Seq[scala.xml.Node] = {
    portInfo.map { case (key, value) =>
        <spirit:portMap>
          <spirit:logicalPort>{key}</spirit:logicalPort>
          <spirit:physicalPort>{key}</spirit:physicalPort>
        </spirit:portMap>
    }.toSeq
  }

  def generateBusInterface(infoList: Seq[(String, String, Map[String, Any])]): Seq[scala.xml.Node] = {
      infoList.map { case (interfaceName, otherName, ports) =>
          <spirit:busInterface>
            <spirit:name>
              {interfaceName}
            </spirit:name>
            <spirit:busType spirit:vendor="amba.com"
                            spirit:library="AMBA4" spirit:name="AXI4Stream" spirit:version="r0p0_1"/>
            <spirit:abstractionType spirit:vendor="amba.com" spirit:library="AMBA4"
                                    spirit:name="AXI4Stream_rtl" spirit:version="r0p0_1"/>
            <spirit:slave/>
            <spirit:portMaps>
              {
                generatePortMaps(ports)
              }
            </spirit:portMaps>
          </spirit:busInterface>
      }
  }

  def generateBusInterfaces(annotationSeq: AnnotationSeq): Option[scala.xml.Node] = {
    val busInterfaceSections = annotationSeq.flatMap {
      case a: ParamsAnnotation if a.paramsClassName == "freechips.rocketchip.amba.axi4.AXI4BundleParameters" =>
        a.target match {
          case ComponentName(componentName, _) =>
            Some((componentName, a.target.toTarget.serialize, a.params))
          case _ =>
            None
        }
      case _ =>
        None
    }.groupBy {
      case (moduleName, componentName, params) => moduleName
    }

    if(busInterfaceSections.isEmpty) {
      None
    }
    else {
      Some(
        <spirit:busInterfaces>
          {
            busInterfaceSections.values.flatMap { case info =>
              generateBusInterface(info)
            }
          }
          </spirit:busInterfaces>

      )
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

      //TODO: (chick) what should the name be here
      val xmlDocument = new IpxactXmlDocument(
        vendor = "edu.berkeley.cs",
        library = "rocket-chip",
//        name = state.circuit.main,
        name = moduleName,
        version = "0.1"
      )

      xmlDocument.addComponents(generateModel(state, moduleName))

      xmlDocument.addComponents(generateFileSets(moduleName))

      generateBusInterfaces(filteredAnnotations).foreach { section =>
        xmlDocument.addComponents(section)
      }

      generateParameters(filteredAnnotations).foreach { section =>
        xmlDocument.addComponents(section)
      }

//      generateMemoryMaps(filteredAnnotations).foreach { section =>
//
//      }

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

      writeXml(targetDir, moduleName, xmlDocument)
      GeneratedIpxactAnnotation(xmlDocument)
    }

    val ipxactXmlAnnotations = ipxactModules.map { moduleTarget =>
      val moduleNameString = moduleTarget match {
        case ModuleTarget(topName, moduleName) =>
          moduleName
        case _ =>
          "NOMATCH"
      }

      val annosForThisModule = state.annotations.flatMap {
        case a @  IpxactModuleAnnotation(ModuleTarget(name, _)) if name == moduleNameString =>
          Some(a)
        case a @ ParamsAnnotation(ComponentName(_, ModuleName(name, _)), _, _) if name == moduleNameString =>
          Some(a)
        case a @ AddressMapAnnotation(ComponentName(_, ModuleName(name, _)), _, _) if name == moduleNameString =>
          Some(a)
        case a @ RegFieldDescMappingAnnotation(ModuleName(name, _), _) if name == moduleNameString =>
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