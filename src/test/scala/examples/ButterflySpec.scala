// See LICENSE for license details.

package examples

import breeze.math.Complex
import chisel3.{Data, Driver, FixedPoint, SInt}
import chisel3.iotesters.PeekPokeTester
import dsptools.numbers.{DspReal, SIntOrder, SIntRing}
import dsptools.{DspContext, DspTester, Grow}
import org.scalatest.{FlatSpec, Matchers}
import dsptools.numbers.implicits._
import dsptools.numbers.{DspComplex, Real}
import dsptools.examples.Butterfly
//import scala.util.Random.{nextInt}

class ButterflyTester[T<:Data:Real](c: Butterfly[T], min: Int = -20, max: Int = 20) extends DspTester(c, base=10) {
  require(max > min)
  def nextInt(): Int = util.Random.nextInt(max - min) - min
  
  for(i <- 0 until 1) {
    val in1 = Complex(nextInt(), nextInt())
    val in2 = Complex(nextInt(), nextInt())
    val twiddle = Complex(nextInt(), nextInt())
    val product = in2*twiddle
    val out1 = in1+product
    val out2 = in1-product
    dspPoke(c.io.in1, in1)
    dspPoke(c.io.in2, in2)
    dspPoke(c.io.twiddle, twiddle)
    step(1)
    dspExpect(c.io.out1, out1, "Output 1")
    dspExpect(c.io.out2, out1, "Output 2")
  }
}

class ButterflySpec extends FlatSpec with Matchers {
  behavior of "Butterfly"
  it should "perform a radix 2 butterfly calculation" in {
    chisel3.iotesters.Driver(() => new Butterfly(DspComplex(SInt(width=10), SInt(width=10)))) {
      c => new ButterflyTester(c)
    } should be (true)
  }
}

