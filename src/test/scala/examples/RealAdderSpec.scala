// See LICENSE for license details.

package examples

import chisel3.core._
import chisel3.iotesters.{Backend, PeekPokeTester}
import dsptools.{ReplOptionsManager, DspTester}
import dsptools.numbers.DspReal
import org.scalatest.{FlatSpec, Matchers}

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
    i <- 0.0 to 1.0 by 0.25
    j <- 0.0 to 4.0 by 0.5
  } {
    poke(c.io.a1, i)
    poke(c.io.a2, j)
    step(1)

    val result = peek(c.io.c)

    println(s"peek $result")
  }
}


class RealAdderSpec extends FlatSpec with Matchers {
  behavior of "adder circuit on blackbox real"

  it should "allow registers to be declared that infer widths" in {
    chisel3.iotesters.Driver(() => new RealAdder) { c =>
      new RealAdderTester(c)
    } should be (true)
  }
}
