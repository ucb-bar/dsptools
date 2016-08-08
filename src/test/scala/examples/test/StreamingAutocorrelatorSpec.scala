// See LICENSE for license details.

package examples.test

//scalastyle:off magic.number

import chisel3.core.SInt
import chisel3.iotesters.{PeekPokeTester, Backend, runPeekPokeTester}
import dsptools.numbers.SIntRing
import dsptools.{Grow, DspContext}
import example.StreamingAutocorrelator
import org.scalatest.{Matchers, FlatSpec}

import dsptools.example.TransposedStreamingFIR
import spire.algebra.{Ring, Field}


class StreamingAutocorrelatorTester(c: StreamingAutocorrelator[SInt], b: Option[Backend] = None)
  extends PeekPokeTester(c, _backend=b) {

  for(num <- -5 to 5) {
    poke(c.io.input, BigInt(num))
    step(1)
    println(peek(c.io.output).toString())
  }
}

class StreamingAutocorrelatorSpec extends FlatSpec with Matchers {
  "StreamingAutocorrelatorFIR" should "compute a running average like thing" in {
    val taps = Seq.tabulate(3) { x => SInt(x)}
    implicit val DefaultDspContext = DspContext()
    implicit val evidence = (context :DspContext) => new SIntRing()(context)

    runPeekPokeTester(
      () => new StreamingAutocorrelator(SInt(width = 10), SInt(width = 16), 2, 3), "firrtl") {
      (c, b) => new StreamingAutocorrelatorTester(c, b) } should be (true)
  }
}
