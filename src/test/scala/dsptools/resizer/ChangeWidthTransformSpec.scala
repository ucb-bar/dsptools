// See LICENSE for license details.

package dsptools.resizer

import dsptools.numbers.resizer.ChangeWidthTransform
import firrtl.annotations.{Annotation, CircuitName, ComponentName, ModuleName}
import firrtl.{AnnotationMap, CircuitState, LowForm, Parser}
import org.scalatest.{FreeSpec, Matchers}

class ChangeWidthTransformSpec extends FreeSpec with Matchers {
  """parse a firrtl file and change the widths""" in {
    val input =
      """
        |circuit InstrumentingAdder : @[:@2.0]
        |  module InstrumentingAdder : @[:@3.2]
        |    input clock : Clock @[:@4.4]
        |    input reset : UInt<1> @[:@5.4]
        |    input io_a1 : SInt<32> @[:@6.4]
        |    input io_a2 : SInt<32> @[:@6.4]
        |    output io_c : SInt<32> @[:@6.4]
        |
        |    reg register1 : SInt<32>, clock with :
        |      reset => (UInt<1>("h0"), register1) @[InstrumentingSpec.scala 20:22:@11.4]
        |    node _T_6 = add(io_a1, io_a2) @[FixedPointTypeClass.scala 21:58:@12.4]
        |    node _T_7 = tail(_T_6, 1) @[FixedPointTypeClass.scala 21:58:@13.4]
        |    node _T_8 = asSInt(_T_7) @[FixedPointTypeClass.scala 21:58:@14.4]
        |    io_c <= register1
        |    register1 <= _T_8
        |
      """.stripMargin

    val annotations = AnnotationMap(Seq(
      Annotation(
        ComponentName("io_a1", ModuleName("InstrumentingAdder", CircuitName("InstrumentingAdder"))),
        classOf[ChangeWidthTransform],
        "io_a1=16"
      ),
      Annotation(
        ComponentName("io_a1", ModuleName("register1", CircuitName("InstrumentingAdder"))),
        classOf[ChangeWidthTransform],
        "register1=8"
      )
    ))

    val circuitState = CircuitState(Parser.parse(input), LowForm, Some(annotations))

    val transform = new ChangeWidthTransform

    val newCircuitState = transform.execute(circuitState)

    val newFirrtlString = newCircuitState.circuit.serialize

    newFirrtlString should include ("input io_a1 : SInt<16>")
    newFirrtlString should include ("register1 : SInt<8>")

    //noinspection ScalaStyle
    println(s"After ChangeWidthTransform\n$newFirrtlString")
  }
}
