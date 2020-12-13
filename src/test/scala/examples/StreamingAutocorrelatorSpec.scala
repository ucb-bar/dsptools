// SPDX-License-Identifier: Apache-2.0

package examples

//scalastyle:off magic.number

import chisel3._
import chisel3.iotesters.PeekPokeTester
import dsptools.numbers.implicits._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import spire.algebra.{Ring, Field}


class StreamingAutocorrelatorTester(c: StreamingAutocorrelator[SInt])
  extends PeekPokeTester(c) {

  for(num <- -5 to 5) {
    poke(c.io.input, BigInt(num))
    step(1)
    println(peek(c.io.output).toString())
  }
}

class StreamingAutocorrelatorSpec extends AnyFlatSpec with Matchers {
  "StreamingAutocorrelatorFIR" should "compute a running average like thing" in {
    val taps = Seq.tabulate(3) { x => x.S}
    //implicit val DefaultDspContext = DspContext()
    //implicit val evidence = (context :DspContext) => new SIntRing()(context)

    chisel3.iotesters.Driver(() => new StreamingAutocorrelator(SInt(10.W), SInt(20.W), 2, 3)) { c =>
      new StreamingAutocorrelatorTester(c)
    } should be (true)
  }
}
