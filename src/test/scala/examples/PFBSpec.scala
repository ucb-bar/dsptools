// See LICENSE for license details.

package examples

//scalastyle:off magic.number

import chisel3.Driver
import chisel3.core.SInt
import chisel3.iotesters.PeekPokeTester
import dsptools.numbers.{SIntOrder, SIntRing}
import dsptools.{DspContext, Grow}
import org.scalatest.{FlatSpec, Matchers}
import dsptools.examples.PFB
import spire.algebra.{Field, Order, Ring}

class PFBTester(c: PFB[SInt]) extends PeekPokeTester(c) {
  poke(c.io.sync_in, 0)

  for(num <- -50 to 50) {
    c.io.data_in.foreach { port => poke(port, BigInt(num)) }
    step(1)
    c.io.data_out.foreach { port => println(peek(port).toString)}
  }
}
class PFBSpec extends FlatSpec with Matchers {
  import chisel3.{Bool, Bundle, Module, Mux, UInt, Vec}
  "Vecs" should "have some sort of justice" in {
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
  "PFB" should "sort of do something" in {
    implicit val DefaultDspContext = DspContext()
    implicit val evidence = (context :DspContext) => new SIntRing()(context)

    implicit val ring = new SIntRing()
    implicit val order = new SIntOrder()

    chisel3.iotesters.Driver(() => new PFB(SInt(width = 10), Some(SInt(width = 16)), n=16, p=4,
      min_mem_depth = 1, taps = 4, pipe = 3, use_sp_mem = false, symm = false, changes = false)) {
      c => new PFBTester(c)
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
