// SPDX-License-Identifier: Apache-2.0

package examples

import chisel3._
import dsptools.{DspTester, ReplOptionsManager}
import dsptools.numbers.DspReal
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.math.BigDecimal

class RealAdder extends Module {
  val io = IO(new Bundle {
    val a1 = Input(new DspReal)
    val a2 = Input(new DspReal)
    val c  = Output(new DspReal)
  })

  val register1 = Reg(new DspReal)

  register1 := io.a1 + io.a2

  io.c := register1
}

object RealAdder {
  def main(args: Array[String]) {
    val optionsManager = new ReplOptionsManager
    if(optionsManager.parse(args)) {
      dsptools.Driver.executeFirrtlRepl(() => new RealAdder, optionsManager)
    }
  }
}

class RealAdderTester(c: RealAdder) extends DspTester(c) {
  for {
    i <- (BigDecimal(0.0) to 1.0 by 0.25).map(_.toDouble)
    j <- (BigDecimal(0.0) to 4.0 by 0.5).map(_.toDouble)
  } {
    poke(c.io.a1, i)
    poke(c.io.a2, j)
    step(1)

    expect(c.io.c, i + j)
  }
}


class RealAdderSpec extends AnyFlatSpec with Matchers {
  behavior of "adder circuit on blackbox real"

  it should "allow registers to be declared that infer widths" in {
    dsptools.Driver.execute(() => new RealAdder, Array("--backend-name", "firrtl")) { c =>
      new RealAdderTester(c)
    } should be (true)
  }
}
