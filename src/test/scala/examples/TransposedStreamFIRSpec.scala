// SPDX-License-Identifier: Apache-2.0

package examples

//scalastyle:off magic.number

import chisel3._
import chisel3.iotesters.{PeekPokeTester, TesterOptions}
import dsptools.numbers.implicits._
import dsptools.{DspContext, Grow}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import dsptools.examples.{ConstantTapTransposedStreamingFIR, TransposedStreamingFIR}
import spire.algebra.{Field, Ring}

class ConstantTapTransposedStreamingTester(c: ConstantTapTransposedStreamingFIR[SInt, Int])
  extends PeekPokeTester(c) {
  val smallest = -5
  val biggest  = 5
  println(s"Taps are ${c.taps.toString}")

  def checkAnswer(n: Int) : Int = {
    // assumes inputs increase by 1 each time
    c.taps.zipWithIndex.foldLeft(0) {case (s, (tap, idx)) =>
      s + tap * (if(n - idx >= smallest) n - idx else 0)
    }
  }
  // initialize old state to 0
  poke(c.io.input.valid, 1)
  poke(c.io.input.bits, BigInt(0))
  step(c.taps.length)

  for(num <- smallest to biggest) {
    poke(c.io.input.bits, BigInt(-7))
    poke(c.io.input.valid, 0)
    for (i<- 0 until 10) {
      step(1)
      expect(c.io.output.valid, 0, "Output should not be valid if input is invalid")
    }
    poke(c.io.input.valid, 1)
    poke(c.io.input.bits, BigInt(num))
    step(1)
    println(peek(c.io.output.bits).toString())
    println(s"Answer should be ${checkAnswer(num)}")
    expect(c.io.output.bits, checkAnswer(num), "Output did should match expected data")
    expect(c.io.output.valid, 1, "Output should be valid if input is valid")
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

class TransposedStreamFIRSpec extends AnyFlatSpec with Matchers {
  "ConstantTapTransposedStreamingFIR" should "compute a running average like thing" in {
    val taps = 0 until 3

    chisel3.iotesters.Driver.execute(Array[String](),
      () => new ConstantTapTransposedStreamingFIR(SInt(10.W), SInt(16.W), taps)) {
      c => new ConstantTapTransposedStreamingTester(c)
    } should be (true)
  }
}
