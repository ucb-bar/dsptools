// See LICENSE for license details.

package dsptools.numbers.resizer

import firrtl._
import firrtl.annotations.{Annotation, Named}
import firrtl.ir._
import firrtl.Mappers._
import firrtl.passes._
import _root_.logger.{LazyLogging, LogLevel, Logger}
import firrtl.PrimOps._

import scala.collection.mutable

/**
  * Create annotation methods for reducing widths of wires
  */
object ChangeWidthAnnotation {
  def apply(target: Named, value: String): Annotation = Annotation(target, classOf[ChangeWidthTransform], value)

  def unapply(a: Annotation): Option[(Named, String)] = a match {
    case Annotation(named, t, value) if t == classOf[ChangeWidthTransform] => Some((named, value))
    case _ => None
  }
}

/**
  * Change the widths of wires based on usage statistics
  * Sets widths of primops to be Unknown so that they can be re-inferred
  *
  */
class ChangeWidthTransform extends Transform with LazyLogging {
  override def inputForm: CircuitForm = LowForm
  override def outputForm: CircuitForm = LowForm

  def makeChangeRequests(annotations: Seq[Annotation]): Map[String, ChangeRequest] = {
    annotations.flatMap { annotation =>
      val componentName :: widthString :: _ = annotation.value.split("""=""", 2).toList
      if(componentName.startsWith("io_")) {
        None
      }
      else {
        Some(componentName -> ChangeRequest(componentName, BigInt(widthString, 10)))
      }
    }.toMap
  }

  def typeToWidth(tpe: Type): BigInt = {
    tpe match {
      case SIntType(IntWidth(oldWidth)) =>
        oldWidth
      case UIntType(IntWidth(oldWidth)) =>
        oldWidth
      case ClockType => BigInt(1)
      case _ => 1
    }
  }

  //scalastyle:off method.length cyclomatic.complexity
  private def run(c: Circuit, changeRequests: Map[String, ChangeRequest]): Circuit = {
    val mappedModules = new mutable.HashMap[Module, Module] ++ c.modules.map { module => module -> module }

    val oldWidths = new mutable.HashMap[String, BigInt]()

    def findModule(name: String): DefModule = {
      c.modules.find(module => module.name == name) match {
        case Some(m: Module) => m
        case Some(m: ExtModule) => m
        case _ =>
          throw new Exception(s"Error: could not find $name in $c")
      }
    }

    def removeWidth(tpe: Type): Type = {
      tpe match {
        case SIntType(IntWidth(_)) => SIntType(UnknownWidth)
        case UIntType(IntWidth(_)) => UIntType(UnknownWidth)
        case _                     => tpe
      }
    }

    def decreaseTypeWidth(originalType: Type, delta: Int): Type = {
      originalType match {
        case SIntType(IntWidth(oldWidth)) => SIntType(IntWidth(oldWidth - delta))
        case UIntType(IntWidth(oldWidth)) => UIntType(IntWidth(oldWidth - delta))
        case other                        => other
      }
    }

    def changeTpe(originalType: Type, changeRequest: ChangeRequest) = {
      val newWidth = changeRequest.newWidth
      originalType match {
        case SIntType(IntWidth(oldWidth)) =>
          oldWidths(changeRequest.name) = oldWidth
          val newType = SIntType(IntWidth(newWidth))
          changeRequest.mark()
          // logger.info(s"Changing $originalType to $newType")
          newType
        case UIntType(IntWidth(oldWidth)) =>
          oldWidths(changeRequest.name) = oldWidth
          val newType = UIntType(IntWidth(newWidth))
          changeRequest.mark()
          // logger.debug(s"Changing $originalType to $newType")
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
        val resultExpression = expression map changeWidthsInExpression
        resultExpression match {
          case primOp : DoPrim =>
            primOp.copy(tpe = removeWidth(primOp.tpe))

          case exp => exp
        }
      }

      def changeWidthInPorts(ports: Seq[Port]): Seq[Port] = {
        ports.map { port =>
          changeRequests.get(expand(port.name)) match {
            case Some(changeRequest) =>
              // logger.info(s"Changing:port ${expand(port.name)} to ${changeRequest.newWidth}")
              port.copy(tpe = changeTpe(port.tpe, changeRequest))
            case _ =>
              port
          }
        }
      }

      def signExtend(numberToDo: Int, firstArg: Expression, lastArg: Expression, tpe: Type): Expression = {
        if(numberToDo <= 1) {
          DoPrim(Cat, Seq(firstArg, lastArg), Seq(), tpe)
        }
        else {
          DoPrim(
            Cat,
            Seq(firstArg, signExtend(numberToDo - 1, firstArg, lastArg, tpe)),
            Seq(),
            decreaseTypeWidth(tpe, delta = numberToDo - 1)
          )
        }
      }

      def constructSmallerIntermediates(
                                         wire: Statement with IsDeclaration,
                                         tpe: Type,
                                         changeRequest: ChangeRequest
                                       ): Block = {
        val reduced = DefWire(wire.info, wire.name + "__reduced", changeTpe(tpe, changeRequest))
        val msb     = DefWire(wire.info, wire.name + "__msb",  UIntType(IntWidth(1)))

        val extendedSign = signExtend(
          (typeToWidth(tpe) - changeRequest.newWidth).toInt,
          WRef(msb),
          DoPrim(AsUInt, Seq(WRef(reduced)), Seq(), UIntType(IntWidth(changeRequest.newWidth))),
          tpe
        )

        Block(Seq(
          wire,
          reduced,
          msb,
          Connect(
            wire.info, WRef(msb),
            tpe match {
              case _: UIntType => UIntLiteral(BigInt(0), IntWidth(1))
              case _: SIntType => DoPrim(Head, Seq(WRef(reduced)), Seq(BigInt(1)), UIntType(IntWidth(1)))
            }
          ),
          Connect(
            wire.info, WRef(wire.name, tpe, WireKind),
            tpe match {
              case _: UIntType =>
                extendedSign
              case _: SIntType =>
                DoPrim(AsSInt, Seq(
                  extendedSign
                ), Seq(), tpe)
            }
          )
        ))
      }

      def changeWidthsInStatement(statement: Statement): Statement = {
        val resultStatement = statement map changeWidthsInStatement map changeWidthsInExpression
        resultStatement match {
          case connect: Connect =>
            changeRequests.get(expand(connect.loc.serialize)) match {
              case Some(changeRequest) =>
                val newLoc = connect.loc match {
                  case w: WRef => w.copy(name = w.name + "__reduced")
                  case s       => s
                }
                // logger.info(s"Changing:Connect ${register.name} new width ${changeRequest.newWidth}")
                Block(Seq(
                  connect.copy(loc = newLoc)
                ))
              case _ => connect
            }
          case register: DefRegister =>
            changeRequests.get(expand(register.name)) match {
              case Some(changeRequest) =>
                constructSmallerIntermediates(register, register.tpe, changeRequest)

                // logger.info(s"Changing:DefReg ${register.name} new width ${changeRequest.newWidth}")
              case _ => register
            }
          case wire: DefWire =>
            changeRequests.get(expand(wire.name)) match {
              case Some(changeRequest) =>
                constructSmallerIntermediates(wire, wire.tpe, changeRequest)
              // logger.info(s"Changing:DefReg ${wire.name} new width ${changeRequest.newWidth}")
              case _ => wire
            }
          case instance: DefInstance =>
            findModule(instance.module) match {
              case _: ExtModule => instance
              case m: Module =>
                mappedModules(m) = changeWidthsInModule(m, s"$pathString${instance.name}.")
                instance
            }
          case instance: WDefInstance =>
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
        val nodesToWiresTransform = new NodesToWiresTransform
        val state1 = state.copy(circuit = ToWorkingIR.run(state.circuit))
        val state2 = state.copy(circuit = InferTypes.run(state1.circuit))
        val state3 = nodesToWiresTransform.execute(state2)
        val state4 = state3.copy(circuit = run(state3.circuit, changeRequests))
        val state5 = state4.copy(circuit = (new InferWidths).run(state4.circuit))
        val state6 = state5.copy(circuit = InferTypes.run(state5.circuit))
        val state7 = state6.copy(circuit = FixPrimOps.run(state6.circuit))
        state7
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
