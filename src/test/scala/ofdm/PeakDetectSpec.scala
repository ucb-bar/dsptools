package ofdm

import breeze.math.Complex
import breeze.numerics.pow
import chisel3.{Driver => _, _}
import chisel3.experimental.FixedPoint
import chisel3.iotesters.{Driver, PeekPokeTester, TesterOptionsManager}
import dsptools.numbers._
import dsptools.numbers.implicits._
import org.scalatest.{FlatSpec, Matchers}

import scala.util.Random

class PeakDetectTester[T <: Data](c: PeakDetect[T]) extends PeekPokeTester(c) {

}

class PeakDetectSpec extends FlatSpec with Matchers {
  behavior of "PeakDetect"

  val manager = new TesterOptionsManager {
    interpreterOptions = interpreterOptions.copy(setVerbose = false, writeVCD = true)
    testerOptions = testerOptions.copy(backendName = "firrtl")
  }

  def nextComplex(snrdB: Double): Complex = {
    Complex(Random.nextGaussian(), Random.nextGaussian()) * pow(10.0, snrdB / 10.0)
  }


  it should "detect peaks with FixedPoint" in {
    val p = PeakDetectParams(
      protoCorr=FixedPoint(32.W, 16.BP),
      protoEnergyFF=FixedPoint(32.W, 31.BP),
      protoEnergyMult=FixedPoint(32.W, 16.BP),
      windowSize = 4
    )
    Driver.execute(() => new PeakDetect(p)
    , optionsManager = manager) { c => new dsptools.DspTester(c) {

      poke(c.io.energyFF, 99.0/100.0)
      poke(c.io.energyMult, 100.0 * 8.0) // 1/(1-energyFF) = 100, 8.0=9dB

      poke(c.io.in.valid, 1)
      // don't care about raw signal in this tester
      poke(c.io.in.bits.raw, Complex(0,0))


      // poke in some noise at -15dB
      for (i <- 0 until 200) {
        poke(c.io.in.bits.corr, nextComplex(-15.0))
        step(1)
      }

      val peakVal: Complex = Complex(1,0)
      println("Poking in ramp")

      // ramp up to 0dB, shouldn't see a peak yet
      for (i <- 0 until 10) {
        poke(c.io.in.bits.corr, peakVal / (10.0 - i))
        expect(c.io.outLast, 0)
        step(1)
      }

      // back down to -15dB, should get a peak out in 4 cycles
      for (i <- 0 until 4) {
        poke(c.io.in.bits.corr, nextComplex(-15.0))
        expect(c.io.outLast, 0)
        step(1)
      }
      expect(c.io.outLast, 1)
      expect(c.io.out.valid, 1)
      expect(c.io.out.bits.corr, peakVal)

    }} should be (true)
  }
}