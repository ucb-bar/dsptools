package ofdm

import breeze.math.Complex
import breeze.numerics.log10
import chisel3._
import chisel3.experimental.FixedPoint
import chisel3.iotesters.TesterOptionsManager
import co.theasi.plotly.{Plot, draw}
import dsptools.DspTester
import dsptools.numbers._
import dsptools.numbers.implicits._
import ieee80211.IEEE80211
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.mutable.ArrayBuffer

class FIRSpec extends FlatSpec with Matchers {
  behavior of "STF Matched Filter"

  val manager = new TesterOptionsManager {
    interpreterOptions = interpreterOptions.copy(setVerbose = false, writeVCD = true)
    testerOptions = testerOptions.copy(backendName = "verilator")
  }

  def runFilter[T <: Data : Ring : ConvertableTo](in: Seq[Complex], protoIn: T, protoOut: T, protoCoeff: T): Seq[Complex] = {
    val out = ArrayBuffer[Complex]()
    chisel3.iotesters.Driver.execute(
      () => new STF16MatchedFilter(protoIn, protoOut, protoCoeff), manager) { c=> new DspTester(c) {
      poke(c.io.in.valid, 1)
      for (i <- in) {
        poke(c.io.in.bits, i)
        if (peek(c.io.out.valid) != BigInt(0)) {
          out += peek(c.io.out.bits)
        }
        step(1)
      }
    }}
    out
  }
  it should "make a nice plot" in {
    val pattern = Seq.fill(100){Complex(0,0)} ++ IEEE80211.stf.toSeq ++ Seq.fill(100){Complex(0,0)}

    val out = runFilter(pattern, FixedPoint(32.W, 16.BP), FixedPoint(32.W, 16.BP), FixedPoint(32.W, 16.BP))

    println(out)

    val xs = (0 to out.length)
    val ys = out.map(x => 10*log10(x.abs))
    println(s"Absolute values are $ys")

  }
}
