// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3._
import chiseltest._
import chiseltest.iotesters._
import dsptools.misc.PeekPokeDspExtensions
import org.scalatest.freespec.AnyFreeSpec

class LnModule extends Module {
  val io = IO(new Bundle {
    val num = Input(DspReal())
    val ln = Output(DspReal())
  })

  io.ln := io.num.ln
}

class LnTester(c: LnModule) extends PeekPokeTester(c) with PeekPokeDspExtensions {
  poke(c.io.num, 11.0)
  private val x = peek(c.io.ln)
  println(s"poked 1.0 got $x expected ${math.log(11.0)}")
}

class LnSpec extends AnyFreeSpec with ChiselScalatestTester {
  "ln should work" in {
    test(new LnModule)
      .withAnnotations(Seq(VerilatorBackendAnnotation))
      .runPeekPoke(new LnTester(_))
  }
}
