// See LICENSE for license details.

package dsptools.numbers.resizer

import firrtl._
import firrtl.annotations.{Annotation, Named}
import firrtl.ir._
import firrtl.Mappers._
import firrtl.passes.InferTypes
import logger.{LazyLogging, LogLevel, Logger}

import scala.collection.mutable

class NodesToWiresTransform extends Transform with LazyLogging {
  override def inputForm: CircuitForm = LowForm
  override def outputForm: CircuitForm = LowForm

  //scalastyle:off method.length cyclomatic.complexity
  private def run(c: Circuit): Circuit = {
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

      def changeWidthsInStatement(statement: Statement): Statement = {
        val resultStatement = statement map changeWidthsInStatement map changeWidthsInExpression
        resultStatement match {
//          case b: Block =>
//            Block(b.stmts.map(changeWidthsInStatement))
          case node: DefNode =>
            val wireFromNode = DefWire(node.info, node.name, node.value.tpe)
            val connect      = Connect(node.info, WRef(wireFromNode), node.value)
            Block(Seq(
              wireFromNode,
              connect
            ))
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
        body = changeWidthsInStatement(module.body)
      )
    }

    findModule(c.main) match {
      case m: Module => mappedModules(m) = changeWidthsInModule(m)
    }
    val modulesx = c.modules.map { m => mappedModules(m) }

    Circuit(c.info, modulesx, c.main)
  }

  override def execute(state: CircuitState): CircuitState = {
    Logger.setLevel(LogLevel.Info)

    val newState = state.copy(circuit = InferTypes.run(state.circuit))
    newState.copy(circuit = run(newState.circuit))

  }
}

//scalastyle:off regex
object NodesToWiresTransform {
  def main(args: Array[String]): Unit = {
    args.toList match {
      case firrtlFile :: _ =>
        val firrtl = io.Source.fromFile(firrtlFile).getLines().mkString("\n")

        val circuit = Parser.parse(firrtl)
        val compiler = new LowFirrtlCompiler

        val compileResult = compiler.compileAndEmit(CircuitState(circuit, ChirrtlForm))

        val nodesToWiresTransform = new NodesToWiresTransform
        val result = nodesToWiresTransform.execute(compileResult)
        println(s"No node Firrtl: \n${result.circuit.serialize}")
      case _ =>
        println(s"Usage: NodesToWiresTransform firrtl-file")
    }
  }
}
