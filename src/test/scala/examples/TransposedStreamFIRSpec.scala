// See LICENSE for license details.

package examples

//scalastyle:off magic.number

import chisel3.SInt
import chisel3.iotesters.PeekPokeTester
import dsptools.numbers.implicits._
import dsptools.{DspContext, Grow}
import org.scalatest.{FlatSpec, Matchers}
import dsptools.examples.{ConstantTapTransposedStreamingFIR, TransposedStreamingFIR}
import spire.algebra.{Field, Ring}

class ConstantTapTransposedStreamingTester(c: ConstantTapTransposedStreamingFIR[SInt, Int])
  extends PeekPokeTester(c) {
  val smallest = -5
  val biggest  = 5
  println(s"Taps are ${c.taps.toString}")

  def checkAnswer(n: Int) : Int = {
    // assumes inputs increase by 1 each time
    c.taps.zipWithIndex.foldLeft(0) {case (s, (tap, idx)) => {
      s + tap * (if(n - idx >= smallest) n - idx else 0)
    }}
  }
  for(num <- smallest to biggest) {
    poke(c.io.input.bits, BigInt(-7))
    poke(c.io.input.valid, 0)
    for (i<- 0 until 10) {
      step(1)
      assert(peek(c.io.output.valid) == 0)
    }
    poke(c.io.input.valid, 1)
    poke(c.io.input.bits, BigInt(num))
    step(1)
    println(peek(c.io.output.bits).toString())
    println(s"Answer should be ${checkAnswer(num)}")
    assert(peek(c.io.output.bits) == checkAnswer(num))
    assert(peek(c.io.output.valid) == 1)
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
    val taps = Seq.tabulate(3){x=>x} // { x => SInt(x)}
    def fromInt(x: Int) = SInt(x)
    //implicit val DefaultDspContext = DspContext()
    //implicit val evidence = (context :DspContext) => new SIntRing()(context)

    chisel3.iotesters.Driver(() => new ConstantTapTransposedStreamingFIR(SInt(width = 10), SInt(width = 16), taps)) {
      c => new ConstantTapTransposedStreamingTester(c)
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
