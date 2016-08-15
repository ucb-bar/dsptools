// See LICENSE for license details.

package examples

//scalastyle:off magic.number

import chisel3.core.SInt
import chisel3.iotesters.{PeekPokeTester}
import dsptools.numbers.SIntRing
import dsptools.{Grow, DspContext}
import org.scalatest.{Matchers, FlatSpec}

import dsptools.examples.{ConstantTapTransposedStreamingFIR, TransposedStreamingFIR}
import spire.algebra.{Ring, Field}

class ConstantTapTransposedStreamingTester(c: ConstantTapTransposedStreamingFIR[SInt])
  extends PeekPokeTester(c) {

  for(num <- -5 to 5) {
    poke(c.io.input, BigInt(num))
    step(1)
    println(peek(c.io.output).toString())
  }
}

class TransposedStreamingTester(c: TransposedStreamingFIR[SInt])
  extends PeekPokeTester(c) {

  for(num <- -5 to 5) {
    poke(c.io.input, BigInt(num))
    step(1)
    println(peek(c.io.output).toString())
  }
}

class TransposedStreamFIRSpec extends FlatSpec with Matchers {
  "ConstantTapTransposedStreamingFIR" should "compute a running average like thing" in {
    val taps = Seq.tabulate(3) { x => SInt(x)}
    implicit val DefaultDspContext = DspContext()
    implicit val evidence = (context :DspContext) => new SIntRing()(context)

    chisel3.iotesters.Driver(() => new ConstantTapTransposedStreamingFIR(SInt(width = 10), SInt(width = 16), taps)) {
      c => new
          ConstantTapTransposedStreamingTester(c)
    } should be (true)
  }
//  "TransposedStreamingFIR" should "compute a running average like thing" in {
//    implicit val DefaultDspContext = DspContext()
//    implicit val evidence = (context :DspContext) => new SIntRing()(context)
//
//    runPeekPokeTester(() => new ConstantTapTransposedStreamingFIR(SInt(width = 10), SInt(width = 16), taps), "firrtl") {
//      (c, b) => new
//          ConstantTapTransposedStreamingTester(c, b)
//    } should be (true)
//  }
}
