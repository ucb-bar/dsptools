// See LICENSE for license details.

package examples

//scalastyle:off magic.number

import breeze.linalg.{linspace, max}
import breeze.numerics.abs
import chisel3.{Data, Driver, FixedPoint, SInt}
import chisel3.iotesters.PeekPokeTester
import dsptools.numbers.{DspReal, SIntOrder, SIntRing}
import dsptools.{DspContext, DspTester, Grow}
import org.scalatest.{FlatSpec, Matchers}
import dsptools.examples._
//import spire.algebra.{Field, Order, Ring}
import dsptools.numbers.implicits._

class PFBTester[T<:Data](c: PFB[T]) extends DspTester(c) {
  poke(c.io.sync_in, 0)

  for(num <- -50 to 50) {
    c.io.data_in.foreach { port => dspPoke(port, num.toDouble) }
    step(1)
    c.io.data_out.foreach { port => println(dspPeek(port).toString)}
  }
}

class PFBConstantInput[T<:Data](c: PFB[T]) extends DspTester(c) {
  val windowSize = c.config.windowSize

  val result = (0 to windowSize / c.config.parallelism * 4).foldLeft(Seq[Double]()) ( (sum:Seq[Double], next: Int) => {
    c.io.data_in.foreach { port => dspPoke(port, 1.0)}
    step(1)
    c.io.data_in.foreach { port => dspPoke(port, 1.0)}
    sum ++ c.io.data_out.map { port => dspPeek(port).left.get }
  })

  println()
  result.foreach { x => print(x.toString + ", ")}
  println()
}

class PFBFilterTester[T<:Data](c: PFBFilter[T,Double],
                               val start: Int = -50,
                               val stop: Int  = 50,
                               val step: Int  = 1
                              ) extends DspTester(c) {
  def computedResult(n: Int): Double = {
    val delay = c.taps.length
    val nTaps = c.taps(0).length
    val counterValue = (n - start) / step
    val taps  = c.taps(counterValue % c.taps.length)
    println(s"Using taps ${taps}")
    val samples = Seq.tabulate(nTaps){x => {
      val samp = n - x * delay
      if (samp >= start) samp
      else 0
    }}
    taps.zip(samples).map { case(x,y) => x*y }  reduce (_+_)
  }
  for (num <- start to stop by step) {
    dspPoke(c.io.data_in, num.toDouble)
    println(dspPeek(c.io.data_out).toString)
    println(s"Should be ${computedResult(num)} at time ${num}")
    assert(math.abs(dspPeek(c.io.data_out).left.get - computedResult(num)) < 0.1 )
    step(1)
  }
}

class PFBLeakageTester[T<:Data](c: PFB[T], numBins: Int = 5, os: Int = 10) extends DspTester(c, verbose=false) {
  import co.theasi.plotly._
  val windowSize = c.config.windowSize
  val parallelism = c.config.parallelism
  val fftSize    = c.config.outputWindowSize

  // multiply by two to be sure to flush old state before measuring
  val numSteps = windowSize * 2 / parallelism

  val testBin=fftSize / 6

  def testTone(freq: Double): Seq[Double] = (0 until numSteps).flatMap(i => {
    (0 until parallelism).map(j => {
      val idx = i * parallelism + j
      val x_t = scala.math.sin(2*math.Pi * freq * idx.toDouble / fftSize)
      dspPoke(c.io.data_in(j), x_t)
    })
    val toret = c.io.data_out.map { port => dspPeek(port).left.get }
    step(1)
    toret
    }).drop(numSteps * parallelism - fftSize) // keep only the last fftSize elements

  def getEnergyAtBin(x_t: Seq[Double], bin: Int) : Double = {
    val sinToCorr = (0 until x_t.length).map( idx => math.sin(2*math.Pi*bin*idx/(fftSize.toDouble)))
    val cosToCorr = (0 until x_t.length).map( idx => math.cos(2*math.Pi*bin*idx/(fftSize.toDouble)))
    val sinCorr = x_t zip sinToCorr map ( {case (x,y) => x * y} ) reduceLeft(_+_)
    val cosCorr = x_t zip cosToCorr map ( {case (x,y) => x * y} ) reduceLeft(_+_)
    sinCorr * sinCorr + cosCorr * cosCorr
  }

  val results = (-numBins.toDouble to numBins.toDouble by (1.0 / os)) map (delta_f => {
    println(s"delta_f=${delta_f}, max=${numBins}")
    val f = testBin.toDouble
    val test = testTone(f + delta_f)
    getEnergyAtBin(test, testBin)
  })
  val rawresults = (-numBins.toDouble to numBins.toDouble by (1.0 / os)) map (delta_f => {
    val f = testBin.toDouble
    getEnergyAtBin((0 until fftSize).map({idx => math.sin(2*math.Pi * (f + delta_f) * idx / fftSize.toDouble)}), testBin)
  })
  val simresults = (-numBins.toDouble to numBins.toDouble by (1.0 / os)) map (delta_f => {
    val f = testBin.toDouble
    val raw = (0 until c.config.windowSize).map({idx => math.sin(2*math.Pi * (f + delta_f) * idx / fftSize.toDouble)})
    getEnergyAtBin(raw.zip(c.config.window).map({case(x,y)=>x*y}), testBin)
  })

  def normalized(x: Seq[Double]): Seq[Double] = x.map(_ / max(x.map(abs(_))))
  val results_normalized = normalized(results)
  val rawresults_normalized = normalized(rawresults)
  val simresults_normalized = normalized(simresults)

  println(s"Results: ${results_normalized}")
  println(s"Rawresults: ${rawresults_normalized}")
  println(s"Simresults: ${simresults_normalized}")

  val x = linspace(-numBins, numBins, results.length).toArray
  val p = Plot()
    .withScatter(x, results_normalized,    ScatterOptions().name("Chisel"))
    .withScatter(x, rawresults_normalized, ScatterOptions().name("No window"))
    .withScatter(x, simresults_normalized, ScatterOptions().name("Sim window"))
  draw(p, "leakage", writer.FileOptions(overwrite=true))
}

class PFBSpec extends FlatSpec with Matchers {
  import chisel3.{Bool, Bundle, Module, Mux, UInt, Vec}
  behavior of "Vecs"
  ignore should "have some sort of justice" in {
    class VecTest extends Module {
      val io = new Bundle {
        val in = Bool().flip
        val out = UInt(width=16)
      }
      val c = Mux(io.in,
//        Vec(UInt(1), UInt(2), UInt(3)), // Fail
        Vec(UInt(1, width=5), UInt(2), UInt(3)), // Pass
        Vec(UInt(10), UInt(20), UInt(30)))
      io.out := c(0)
    }
    class VecTestTester(c: VecTest) extends PeekPokeTester(c) {
      poke(c.io.in, 0)
      step(1)
      expect(c.io.out, 10)
      poke(c.io.in, 1)
      step(1)
      expect(c.io.out, 1)
    }
    println(Driver.emit( () => new VecTest()) )
    chisel3.iotesters.Driver(() =>
      new VecTest) { c => new VecTestTester(c) } should be (true)
  }
  behavior of "PFB"
  ignore should "sort of do something" in {
    chisel3.iotesters.Driver(() => new PFB(SInt(width = 10), Some(SInt(width = 16)),
      config=PFBConfig(
        outputWindowSize=4, numTaps = 4, parallelism = 2
      ))) {
      c => new PFBTester(c)
    } should be (true)
 /*   chisel3.iotesters.Driver(() => new PFB(
      FixedPoint(width = 10, binaryPoint = 2),
      Some(FixedPoint(width = 16, binaryPoint = 4))) ) {
        c => new PFBTester(c)
     } should be (true)*/
  }


  ignore should "build with DspReal" in {
    chisel3.iotesters.Driver(() => new PFB(DspReal(0.0),
//  chisel3.iotesters.Driver(() => new PFBnew(FixedPoint(width=32,binaryPoint=16),
    config=PFBConfig(
      windowFunc = w => Seq(1.0, 2.0, 3.0, 4.0, 1.0, 2.0, 3.0, 4.0),
        numTaps = 2,
      outputWindowSize = 4,
      parallelism=2
    ))) {
      c => new PFBConstantInput(c)
    } should be (true)
  }

  it should "reduce leakage" in {
    chisel3.iotesters.Driver(() => new PFB(DspReal(0.0),
      config=PFBConfig(
        numTaps = 8,
        outputWindowSize = 128,
        parallelism=2
      ))) {
      c => new PFBLeakageTester[DspReal](c, numBins=3, os=100)
    } should be (true)
  }

  behavior of "PFBFilter"
  ignore should "build and run" in {
    chisel3.iotesters.Driver(() => new PFBFilter[SInt,Double](
      SInt(width=8), Some(SInt(width=10)), Some(SInt(width=10)),
        Seq(Seq(1.0,2.0), Seq(3.0,4.0), Seq(5.0,6.0), Seq(7.0,8.0)))
    ) {
      c => new PFBFilterTester(c)
    } should be (true)
  }
}
