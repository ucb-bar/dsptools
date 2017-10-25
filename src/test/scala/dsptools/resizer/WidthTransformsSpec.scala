// See LICENSE for license details.

package dsptools.resizer

import chisel3._
import chisel3.core.FixedPoint
import chisel3.iotesters.PeekPokeTester
import dsptools.DspTester
import dsptools.numbers.resizer.ChangeWidthTransform
import firrtl.annotations.{Annotation, CircuitName, ComponentName, ModuleName}
import firrtl.{AnnotationMap, CircuitState, LowForm, Parser}
import org.scalatest.{FreeSpec, Matchers}
import dsptools.numbers.implicits._

//scalastyle:off magic.number
class ToManyWires extends Module {
  val io = IO(new Bundle {
    val in = Input(FixedPoint(24.W, 12.BP))
    val out = Output(FixedPoint(24.W, 12.BP))
  })

  val reg1 = Reg(FixedPoint(36.W, 14.BP))
  val reg2 = Reg(FixedPoint(14.W, 6.BP))

  reg1 := io.in * io.in
  reg2 := reg1.setBinaryPoint(6)
  io.out := reg2
}

class ToManyWiresTester(c: ToManyWires) extends DspTester(c) {
  for(x <- -5.0 to 5.0 by 0.25) {
    poke(c.io.in, x)

    step(2)

    expect(c.io.out, x * x)
    println(s"$x * $x => ${peek(c.io.out)}")
  }
}


class WidthTransformSpec extends FreeSpec with Matchers {
  """reduce bits when bits or tail are involved""" in {
    dsptools.Driver.executeWithBitReduction(() => new ToManyWires, Array("-fimhb", "16")) { c =>
      new ToManyWiresTester(c)
    }
  }
}
