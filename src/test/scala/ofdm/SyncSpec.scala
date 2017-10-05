package ofdm

import chisel3._
import chisel3.experimental._
import dsptools.numbers._
import breeze.math.Complex
import dsptools.DspTester
import freechips.rocketchip.diplomacy.AddressSet
import ieee80211.IEEE80211
import org.scalatest.{FlatSpec, Matchers}

class SyncSpec extends FlatSpec with Matchers {

  behavior of "Sync Block"

  val testSignal = IEEE80211.stf ++ Seq.fill(5000) { Complex(1, 0) }

  it should "correct CFO of 50 kHz with STF" in {
    val cfoSignal = IEEE80211.addCFO(testSignal, -50.0e3)

    val protoIn  = FixedPoint(12.W, 10.BP)
    val protoOut = FixedPoint(12.W, 10.BP)
    val protoCORDIC = FixedPoint(16.W, 12.BP)
    val protoAutocorr = protoIn
    val protoBig = FixedPoint(32.W, 16.BP)
    val stfParams = SyncParams(
      protoIn = DspComplex(protoIn, protoIn),
      protoOut = DspComplex(protoOut, protoOut),
      filterProtos = (protoIn, protoIn, protoIn),
      protoCORDIC = protoCORDIC,
      protoAngle = protoCORDIC,
      autocorrParams = AutocorrParams(
        protoIn = DspComplex(protoAutocorr, protoAutocorr),
        maxApart = 128,
        maxOverlap = 128,
        address = AddressSet(0x0, 0xffff)
      ),
      peakDetectParams = PeakDetectParams(
        protoCorr = protoCORDIC,
        protoEnergyFF = protoBig,
        protoEnergyMult = protoBig,
        windowSize = 16
      ),
      ncoParams = NCOParams(
        phaseWidth = 16,
        128,
        {x: UInt => x.asTypeOf(FixedPoint(16.W, 16.BP))},
        protoFreq = FixedPoint(16.W, 16.BP),
        protoOut = FixedPoint(32.W, 30.BP)
      )
    )

    def dut() = new Sync(stfParams)

    chisel3.iotesters.Driver.execute(Array("-tbn", "verilator"), dut _) {
      c => new DspTester(c) {
        step(5)
        poke(c.io.autocorrFF, 0.5)
        poke(c.io.autocorrConfig.depthApart, 64)
        poke(c.io.autocorrConfig.depthOverlap, 64)
        poke(c.io.peakDetectConfig.energyOffset, 1.0)
        poke(c.io.peakDetectConfig.energyFF, 0.5)
        poke(c.io.peakDetectConfig.energyMult, 2.0)
        poke(c.io.freqScaleFactor, -1.0 / (2 * math.Pi *64.0))

        var output = Seq[Complex]()

        poke(c.io.in.valid, 1)
        for (in <- cfoSignal) {
          poke(c.io.in.bits, in)
          step(1)
          if (peek(c.io.out.valid) != BigInt(0)) {
            output = output :+ peek(c.io.out.bits)
          }
        }
        for (i <- 0 until 100) {
          step(1)
          if (peek(c.io.out.valid) != BigInt(0)) {
            output = output :+ peek(c.io.out.bits)
          }
        }

        println(s"Input was:")
        println(cfoSignal.toString)
        println(s"Output was:")
        println(output.toString)
      }
    }
  }

}
