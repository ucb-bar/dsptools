// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3._
import chisel3.util._
import chisel3.testers.BasicTester
import chisel3.iotesters.{ChiselFlatSpec, PeekPokeTester, TesterOptionsManager}
import dsptools.{DspTester, ReplOptionsManager}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class LnModule extends Module {
  val io = IO(new Bundle {
    val num = Input(DspReal())
    val ln = Output(DspReal())
  })

  io.ln := io.num.ln()
}

class LnTester(c: LnModule) extends DspTester(c) {
  poke(c.io.num,11.0)
  private val x = peek(c.io.ln)
  println(s"poked 1.0 got $x expected ${math.log(11.0)}")

}
class LnSpec extends AnyFreeSpec {
  "ln should work" in {
    dsptools.Driver.execute(() => new LnModule) { c =>
      new LnTester(c)
    }
  }
}

object LnTester extends App {
  val manager = new ReplOptionsManager
  if(manager.parse(args)) {
      dsptools.Driver.executeFirrtlRepl(() => new LnModule, manager)
  }
}
