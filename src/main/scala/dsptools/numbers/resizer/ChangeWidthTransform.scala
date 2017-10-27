// See LICENSE for license details.

package dsptools.numbers.resizer

import firrtl._
import firrtl.annotations.{Annotation, Named}
import firrtl.ir._
import firrtl.Mappers._
import logger.{LazyLogging, LogLevel, Logger}

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
    def findModule(name: String): DefModule = {
      c.modules.find(module => module.name == name) match {
        case Some(m: Module) => m
        case Some(m: ExtModule) => m
        case _ =>
          throw new Exception(s"Error: could not fine $name in $c")
      }
    }

    def changeTpe(originalType: Type, newWidth: BigInt): Type = {
      originalType match {
        case _: SIntType =>
          val newType = SIntType(IntWidth(newWidth))
          logger.info(s"Changing $originalType to $newType")
          newType
        case _: UIntType =>
          val newType = UIntType(IntWidth(newWidth))
          logger.info(s"Changing $originalType to $newType")
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
          pathString + "." + name
        }
      }

      def changeWidthsInExpression(expression: Expression): Expression = {
        expression
      }

      def changeWidthInPorts(ports: Seq[Port]): Seq[Port] = {
        ports.map { port =>
          changeRequests.get(expand(port.name)) match {
            case Some(changeRequest) =>
              port.copy(tpe = changeTpe(port.tpe, changeRequest.newWidth))
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
                register.copy(tpe = changeTpe(register.tpe, changeReqest.newWidth))
              case _ => register
            }
          case wire: DefWire =>
            changeRequests.get(expand(wire.name)) match {
              case Some(changeReqest) =>
                wire.copy(tpe = changeTpe(wire.tpe, changeReqest.newWidth))
              case _ => wire
            }
          case instance: DefInstance => findModule(instance.module) match {
            case _: ExtModule => instance
            case m: Module =>
              changeWidthsInModule(m, s"$pathString.${module.name}.")
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

    val modulesx = c.modules.map {
      case m: ExtModule => m
      case m: Module => changeWidthsInModule(m)
    }
    Circuit(c.info, modulesx, c.main)
  }

  override def execute(state: CircuitState): CircuitState = {
    Logger.setLevel(LogLevel.Debug)
    getMyAnnotations(state) match {
      case Nil => state
      case myAnnotations =>
        val changeRequests = makeChangeRequests(myAnnotations)
        state.copy(circuit = run(state.circuit, changeRequests))
    }
  }
}

object ChangeWidthTransform {
  def main(args: Array[String]): Unit = {
    args.toList match {
      case firrtlFile :: annotationFile :: _ =>
        val firrtl = io.Source.fromFile(firrtlFile).getLines().mkString("""\n""")
        val annotationsText = io.Source.fromFile(annotationFile).getLines().mkString("""\n""")
        val circuit = Parser.parse(firrtl)
        val compiler = new LowFirrtlCompiler
        val compileResult = compiler.compileAndEmit(CircuitState(circuit, ChirrtlForm))
        compileResult
      case _ =>
        println(s"Usage: ChangeWidthTransform firrtl-file annotation-file")
    }
  }
}

case class ChangeRequest(name: String, newWidth: BigInt)
