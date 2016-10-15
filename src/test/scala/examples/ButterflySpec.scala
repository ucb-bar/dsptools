// See LICENSE for license details.

package examples

import chisel3.{Data, Driver, FixedPoint, SInt}
import chisel3.iotesters.PeekPokeTester
import dsptools.numbers.{DspReal, SIntOrder, SIntRing}
import dsptools.{DspContext, DspTester, Grow}
import org.scalatest.{FlatSpec, Matchers}
import dsptools.numbers.implicits._
import dsptools.numbers.{DspComplex, Real}
import dsptools.examples.Butterfly
import scala.util.Random.{nextInt}

class ButterflyTester[T<:Data:Real](c: Butterfly[T]) extends DspTester(c) {
  
  for(i <- 0 until 1) {
    dspPoke(c.io.in1, nextInt())
    dspPoke(c.io.in2, nextInt())
    dspPoke(c.io.twiddle, nextInt())
    step(1)
    dspPeek(c.io.out1)
    dspPeek(c.io.out2)
  }
}

class ButterflySpec extends FlatSpec with Matchers {
  behavior of "Butterfly"
  it should "flutter around prettily" in {
    chisel3.iotesters.Driver(() => new Butterfly(DspComplex(SInt(width=10), SInt(width=10)))) {
      c => new ButterflyTester(c)
    } should be (true)
  }
}

