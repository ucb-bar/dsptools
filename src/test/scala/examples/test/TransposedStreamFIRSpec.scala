// See LICENSE for license details.

package examples.test

import chisel3.core.SInt
import chisel3.iotesters.{PeekPokeTester, Backend, runPeekPokeTester}
import dsptools.{Grow, DspContext}
import org.scalatest.{Matchers, FlatSpec}

import dsptools.example.TransposedStreamingFIR
import spire.algebra.{Ring, Field}

class SIntRing(implicit context: DspContext) extends Ring[SInt] {
  def plus(f: SInt, g: SInt): SInt = {
    if(context.overflowType == Grow) {
      f +& g
    }
    else {
      f +% g
    }
  }
  def times(f: SInt, g: SInt): SInt = {
    f * g
  }
  def one: SInt = SInt(value = BigInt(1))
  def zero: SInt = SInt(value = BigInt(0))
  def negate(f: SInt): SInt = zero - f

}

class TransposedStreamingTester(c: TransposedStreamingFIR[SInt], b: Option[Backend] = None)
  extends PeekPokeTester(c, _backend=b) {

  for(num <- -5 to 5) {
    poke(c.io.input, BigInt(num))
    step(1)
    println(peek(c.io.output).toString())
  }
}

class TransposedStreamFIRSpec extends FlatSpec with Matchers {
  "TransposedStreamingFIR" should "compute a running average like thing" in {
    val taps = Seq.tabulate(3) { x => SInt(x)}
    implicit val DefaultDspContext = DspContext()
    implicit object Evidence extends SIntRing

    runPeekPokeTester(() => new TransposedStreamingFIR(SInt(width = 10), SInt(width = 16), taps), "firrtl") {
      (c, b) => new
          TransposedStreamingTester(c, b)
    } should be (true)
  }
}
