package craft

import chisel3._
import chisel3.experimental.ChiselAnnotation
import chisel3.util._
import chisel3.testers.BasicTester
import chisel3.experimental.{Analog, attach}
import firrtl.ir.{AnalogType, Circuit, DefModule, Expression, HasName, Port, Statement, Type}
import firrtl.{CircuitForm, CircuitState, LowForm, Transform}
import firrtl.annotations.{Annotation, ModuleName, Named, ComponentName}
import firrtl.Mappers._

object AnalogVerilogTpeAnnotation {
  def apply(target: Named, value: String): Annotation =
    Annotation(target, classOf[AnalogVerilogTpeAnnotation], value)

  def unapply(a: Annotation): Option[(ComponentName, String)] = a match {
    case Annotation(named, t, value) if t == classOf[AnalogVerilogTpeAnnotation] => named match {
      case c: ComponentName => Some((c, value))
      case _ => None
    }
    case _ => None
  }
}

class AnalogVerilogTpeAnnotation extends Transform {
  override def inputForm: CircuitForm  = LowForm
  override def outputForm: CircuitForm = LowForm

  override def execute(state: CircuitState): CircuitState = {
    getMyAnnotations(state) match {
      case Nil => state
      case annos =>
        val analogs = annos.collect { case AnalogVerilogTpeAnnotation(ana, name) => (ana, name) }
        println(s"All the analogs are $analogs")
        state.copy(circuit = run(state.circuit, analogs))
    }
  }

  def run(circuit: Circuit, annos: Seq[(ComponentName, String)]): Circuit = {
    circuit map walkModule(annos)
  }
  def walkModule(annos: Seq[(ComponentName, String)])(m: DefModule): DefModule = {
    val filteredAnnos = Map(annos.filter(a => a._1.module.name == m.name).map {
      case (c, s) => c.name.replace(".", "_") -> s
    }: _*)
    println(s"The filtered annos are $filteredAnnos in module ${m.name}")
    m map walkStatement(filteredAnnos) map walkPort(filteredAnnos)
  }
  def walkStatement(annos: Map[String, String])(s: Statement): Statement = {
    s map walkExpression(annos)
  }
  def walkPort(annos: Map[String, String])(p: Port): Port = {
    println(s"Visited port $p with name ${p.name}, annos.contains(p.name) = ${annos.contains(p.name)}")
    if (annos.contains(p.name)) {
      updateAnalogVerilog(annos(p.name))(p.tpe)
    }
    p
  }
  def walkExpression(annos: Map[String, String])(e: Expression): Expression = {
    println(s"Visited expression $e")
    e match {
      case h: HasName =>
        println("Expression $e has name ${h.name}, annos.contains(h.name) = ${annos.contains(h.name)}")
        if (annos.contains(h.name)) e mapType updateAnalogVerilog(annos(h.name))
      case _ =>
    }
    e
  }
  def updateAnalogVerilog(value: String)(tpe: Type): Type = {
    println(s"Updating analog verilog on $tpe to $value")
    tpe match {
      case a: AnalogType =>
        println(s"Setting $a to $value")
        a.verilogTpe = value
        a
      case t => t
    }
  }
}

trait AnalogAnnotator { self: Module =>
  def annotateAnalog(component: Analog, value: String): Unit = {
    annotate(ChiselAnnotation(component, classOf[AnalogVerilogTpeAnnotation], value))
  }
}
