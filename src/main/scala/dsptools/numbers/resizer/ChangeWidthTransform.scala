// See LICENSE for license details.

package dsptools.numbers.resizer

import firrtl._
import firrtl.annotations.{Annotation, Named}
import firrtl.ir._
import firrtl.Mappers._
import firrtl.PrimOps._
import firrtl.Utils.{getUIntWidth, get_info, throwInternalError}
import firrtl.passes._
import _root_.logger.{LazyLogging, LogLevel, Logger}

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
          throw new Exception(s"Error: could not fine $name in $c")
      }
    }

    def removeWidth(tpe: Type): Type = {
      tpe match {
        case SIntType(IntWidth(_)) => SIntType(UnknownWidth)
        case UIntType(IntWidth(_)) => UIntType(UnknownWidth)
        case _                     => tpe
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

      def changeWidthsInStatement(statement: Statement): Statement = {
        val resultStatement = statement map changeWidthsInStatement map changeWidthsInExpression
        resultStatement match {
          case register: DefRegister =>
            changeRequests.get(expand(register.name)) match {
              case Some(changeReqest) =>
                // logger.info(s"Changing:DefReg ${register.name} new width ${changeReqest.newWidth}")
                register.copy(tpe = changeTpe(register.tpe, changeReqest))
              case _ => register
            }
          case wire: DefWire =>
            changeRequests.get(expand(wire.name)) match {
              case Some(changeReqest) =>
                // logger.info(s"Changing:DefWire ${wire.name} new width ${changeReqest.newWidth}")
                wire.copy(tpe = changeTpe(wire.tpe, changeReqest))
              case _ =>
                wire
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

  class FixBits extends Pass {
    var changesMade: Int = 0

    /** The maximum allowed width for any circuit element */
    val MaxWidth = 1000000
    val DshlMaxWidth = getUIntWidth(MaxWidth + 1)
    class UninferredWidth (info: Info, mname: String, name: String, t: Type) extends PassException(
      s"$info : [module $mname]  Component $name has an Uninferred width: ${t.serialize}")
    class UninferredBound (info: Info, mname: String, name: String, t: Type, bound: String) extends PassException(
      s"$info : [module $mname]  Component $name has an uninferred $bound bound: ${t.serialize}.")
    class InvalidRange (info: Info, mname: String, name: String, t: Type) extends PassException(
      s"$info : [module $mname]  Component $name has an invalid range: ${t.serialize}.")
    class WidthTooSmall(info: Info, mname: String, b: BigInt) extends PassException(
      s"$info : [module $mname]  Width too small for constant $b.")
    class WidthTooBig(info: Info, mname: String, b: BigInt) extends PassException(
      s"$info : [module $mname]  Width $b greater than max allowed width of $MaxWidth bits")
    class DshlTooBig(info: Info, mname: String) extends PassException(
      s"$info : [module $mname]  Width of dshl shift amount cannot be larger than $DshlMaxWidth bits.")
    class NegWidthException(info:Info, mname: String) extends PassException(
      s"$info: [module $mname] Width cannot be negative or zero.")
    class BitsWidthException(info: Info, mname: String, hi: BigInt, width: BigInt, exp: String) extends PassException(
      s"$info: [module $mname] High bit $hi in bits operator is larger than input width $width in $exp.")
    class HeadWidthException(info: Info, mname: String, n: BigInt, width: BigInt) extends PassException(
      s"$info: [module $mname] Parameter $n in head operator is larger than input width $width.")
    class TailWidthException(info: Info, mname: String, n: BigInt, width: BigInt) extends PassException(
      s"$info: [module $mname] Parameter $n in tail operator is larger than input width $width.")
    class AttachWidthsNotEqual(info: Info, mname: String, eName: String, source: String) extends PassException(
      s"$info: [module $mname] Attach source $source and expression $eName must have identical widths.")

    def run(c: Circuit): Circuit = {
      val errors = new Errors()

      def check_width_w(info: Info, mname: String, name: String, t: Type)(w: Width): Width = {
        (w, t) match {
          case (IntWidth(width), _) if width >= MaxWidth =>
            errors.append(new WidthTooBig(info, mname, width))
          case (w: IntWidth, f: FixedType) if (w.width < 0 && w.width == f.width) =>
            errors append new NegWidthException(info, mname)
          case (w: IntWidth, f: IntervalType) if (w.width < 0) =>
            errors append new NegWidthException(info, mname)
          case (_: IntWidth, _) =>
          case _ => errors append new UninferredWidth(info, mname, name, t)
        }
        w
      }

      def hasWidth(tpe: Type): Boolean = tpe match {
        case GroundType(IntWidth(w)) => true
        case GroundType(_) => false
        case _ => println(tpe); throwInternalError
      }

      def check_width_t(info: Info, mname: String, name: String)(t: Type): Type =
        t map check_width_t(info, mname, name) map check_width_w(info, mname, name, t) match {
          case other => other
        }

      def fixWidthInExpression(info: Info, mname: String)(e: Expression): Expression = {
        val newE = e map fixWidthInExpression(info, mname)
        newE match {
          case DoPrim(Bits, Seq(a), Seq(hi, lo), tpe) if (hasWidth(a.tpe) && bitWidth(a.tpe) <= hi) =>
            changesMade += 1
            val newHi = typeToWidth(a.tpe) - 1
            val adjustedBits = DoPrim(Bits, Seq(a), Seq(newHi, lo), tpe)
            println(s"Adjusting bits for ${a} was ($hi, $lo) now ($newHi, $lo)")
            adjustedBits
          case DoPrim(Head, Seq(a), Seq(n), _) if (hasWidth(a.tpe) && bitWidth(a.tpe) < n) =>
            errors append new HeadWidthException(info, mname, n, bitWidth(a.tpe))
            newE
          case DoPrim(Tail, Seq(a), Seq(n), _) if (hasWidth(a.tpe) && bitWidth(a.tpe) <= n) =>
            errors append new TailWidthException(info, mname, n, bitWidth(a.tpe))
            newE
          case DoPrim(Dshl, Seq(a, b), _, _) if (hasWidth(a.tpe) && bitWidth(b.tpe) >= DshlMaxWidth) =>
            errors append new DshlTooBig(info, mname)
            newE
          case _ =>
            newE
        }
      }


      def fixWidthInStatements(minfo: Info, mname: String)(s: Statement): Statement = {
        val info = get_info(s) match { case NoInfo => minfo case x => x }
        val name = s match { case i: IsDeclaration => i.name case _ => "" }
        s map fixWidthInExpression(info, mname) map fixWidthInStatements(info, mname) map check_width_t(info, mname, name)
      }

      def check_width_p(minfo: Info, mname: String)(p: Port): Port = p.copy(tpe =  check_width_t(p.info, mname, p.name)(p.tpe))

      def fixWidthInModules(m: DefModule): DefModule = {
        m map check_width_p(m.info, m.name) map fixWidthInStatements(m.info, m.name)
      }

      val fixedModules = c.modules map fixWidthInModules

      println(s"FixBits: changes made $changesMade modules differ ${fixedModules == c.modules}")

      c.copy(modules = fixedModules)
    }

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
