// See LICENSE for license details.

package dsptools.numbers.resizer

import firrtl._
import firrtl.annotations.{Annotation, Named}
import firrtl.ir._
import firrtl.Mappers._
import firrtl.passes.InferTypes
import logger.{LazyLogging, LogLevel, Logger}

import scala.collection.mutable

object ChangeWidthAnnotation {
  def apply(target: Named, value: String): Annotation = Annotation(target, classOf[ChangeWidthTransform], value)

  def unapply(a: Annotation): Option[(Named, String)] = a match {
    case Annotation(named, t, value) if t == classOf[ChangeWidthTransform] => Some((named, value))
    case _ => None
  }
}

class ChangeWidthTransform extends Transform with LazyLogging {
  override def inputForm: CircuitForm = LowForm
  override def outputForm: CircuitForm = LowForm


  def makeChangeRequests(annotations: Seq[Annotation]): Map[String, ChangeRequest] = {
    annotations.map { annotation =>
      val componentName :: widthString :: _ = annotation.value.split("""=""", 2).toList
      componentName -> ChangeRequest(componentName, BigInt(widthString, 10))
    }.toMap
  }

  //scalastyle:off method.length cyclomatic.complexity
  private def run(c: Circuit, changeRequests: Map[String, ChangeRequest]): Circuit = {
    val mappedModules = new mutable.HashMap[Module, Module] ++ c.modules.map { module => module -> module }

    def findModule(name: String): DefModule = {
      c.modules.find(module => module.name == name) match {
        case Some(m: Module) => m
        case Some(m: ExtModule) => m
        case _ =>
          throw new Exception(s"Error: could not fine $name in $c")
      }
    }

    def changeTpe(originalType: Type, changeRequest: ChangeRequest) = {
      val newWidth = changeRequest.newWidth
      originalType match {
        case _: SIntType =>
          val newType = SIntType(IntWidth(newWidth))
          changeRequest.mark()
          logger.info(s"Changing $originalType to $newType")
          newType
        case _: UIntType =>
          val newType = UIntType(IntWidth(newWidth))
          changeRequest.mark()
          logger.debug(s"Changing $originalType to $newType")
          newType
        case other => other
      }
    }

    def changeWidthsInModule(module: Module, pathString: String = ""): Module = {
      def expand(name: String): String = {
        if(pathString.isEmpty) {
          name
        }
        else {
          pathString + name
        }
      }

      def changeWidthsInExpression(expression: Expression): Expression = {
        expression
      }

      def changeWidthInPorts(ports: Seq[Port]): Seq[Port] = {
        ports.map { port =>
          changeRequests.get(expand(port.name)) match {
            case Some(changeRequest) =>
              logger.info(s"Changing:port ${expand(port.name)} to ${changeRequest.newWidth}")
              port.copy(tpe = changeTpe(port.tpe, changeRequest))
            case _ =>
              port
          }
        }
      }

      def changeWidthsInStatement(statement: Statement): Statement = {
        val resultStatement = statement map changeWidthsInStatement map changeWidthsInExpression
        resultStatement match {
          case register: DefRegister =>
            changeRequests.get(expand(register.name)) match {
              case Some(changeReqest) =>
                logger.info(s"Changing:DefReg ${register.name} new width ${changeReqest.newWidth}")
                register.copy(tpe = changeTpe(register.tpe, changeReqest))
              case _ => register
            }
          case wire: DefWire =>
            changeRequests.get(expand(wire.name)) match {
              case Some(changeReqest) =>
                logger.info(s"Changing:DefWire ${wire.name} new width ${changeReqest.newWidth}")
                wire.copy(tpe = changeTpe(wire.tpe, changeReqest))
              case _ =>
                wire
            }
          case node:
          case instance: DefInstance =>
            findModule(instance.module) match {
              case _: ExtModule => instance
              case m: Module =>
                mappedModules(m) = changeWidthsInModule(m, s"$pathString${instance.name}.")
                instance
            }
          case otherStatement => otherStatement
        }
      }

      module.copy(
        ports = changeWidthInPorts(module.ports),
        body = changeWidthsInStatement(module.body)
      )
    }

    findModule(c.main) match {
      case m: Module => mappedModules(m) = changeWidthsInModule(m)
    }
    val modulesx = c.modules.map { m => mappedModules(m) }

    changeRequests.values.foreach { changeRequest =>
      if(changeRequest.useCount < 1 ) {
        logger.info(s"Warning: ChangeRequest for ${changeRequest.name} not used")
      }
      else if(changeRequest.useCount > 1 ) {
        logger.info(s"Warning: ChangeRequest for ${changeRequest.name} used ${changeRequest.useCount} times")
      }
    }
    Circuit(c.info, modulesx, c.main)
  }

  override def execute(state: CircuitState): CircuitState = {
    Logger.setLevel(LogLevel.Info)
    getMyAnnotations(state) match {
      case Nil => state
      case myAnnotations =>
        val changeRequests = makeChangeRequests(myAnnotations)
        val newState = state.copy(circuit = InferTypes.run(state.circuit))
        newState.copy(circuit = run(newState.circuit, changeRequests))
    }
  }
}

//scalastyle:off regex
object ChangeWidthTransform {
  def main(args: Array[String]): Unit = {
    args.toList match {
        //TODO (chick) WIP, need helper function from firrtl to instantiate annotations.
//      case firrtlFile :: annotationFile :: _ =>
//        val firrtl = io.Source.fromFile(firrtlFile).getLines().mkString("""\n""")
//        val annotationsText = {
//          val annotationsYaml = io.Source.fromFile(annotationFile).getLines().mkString("\n").parseYaml
//          val annotationArray = annotationsYaml.convertTo[Array[Annotation]]
//        }
//        val circuit = Parser.parse(firrtl)
//        val compiler = new LowFirrtlCompiler
//
//        val aa = Driver.loadAnnotations()
//        val compileResult = compiler.compileAndEmit(CircuitState(circuit, ChirrtlForm,))
//        compileResult
//
//        val changeWidthTransform = new ChangeWidthTransform
//        val result = changeWidthTransform.execute(compileResult)
//        println(s"Bit reduced Firrtl: \n${result.circuit.serialize}")
      case _ =>
        println(s"Usage: ChangeWidthTransform firrtl-file annotation-file")
    }
  }
}

case class ChangeRequest(name: String, newWidth: BigInt) {
  var useCount: Int = 0
  def mark(): Unit = { useCount += 1 }
}
