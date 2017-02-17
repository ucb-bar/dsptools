// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import chisel3.experimental._
import dsptools.DspTester
import org.scalatest.{FreeSpec, Matchers}
import dsptools.numbers.implicits._

class FixedRing1(val width: Int, val binaryPoint: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(FixedPoint(width.W, binaryPoint.BP))
    val floor = Output(FixedPoint(width.W, binaryPoint.BP))
    val ceil = Output(FixedPoint(width.W, binaryPoint.BP))
    val isWhole = Output(Bool())
    val round = Output(FixedPoint(width.W, binaryPoint.BP))
    val real = Output(DspReal(1.0))
  })

  io.floor := io.in.floor()
  io.ceil := io.in.ceil()
  io.isWhole := io.in.isWhole()
  io.round := io.in.round()
}

class FixedRing1Tester(c: FixedRing1) extends DspTester(c) {
  val increment: Double = if(c.binaryPoint == 0) 1.0 else 1.0 / (1 << c.binaryPoint)
  println(s"Increment is $increment")
  for(i <- -2.0 to 3.0 by increment) {
    println(s"Testing value $i")

    poke(c.io.in, i)

    expect(c.io.floor, breeze.numerics.floor(i), s"floor of $i should be ${breeze.numerics.floor(i)}")
    expect(c.io.ceil, breeze.numerics.ceil(i), s"ceil of $i should be ${breeze.numerics.ceil(i)}")
    expect(c.io.isWhole, breeze.numerics.floor(i) == i , s"isWhole of $i should be ${breeze.numerics.floor(i) == i}")
    expect(c.io.round, breeze.numerics.round(i), s"round of $i should be ${breeze.numerics.round(i)}")
    step(1)
  }}

class FixedPointSpec extends FreeSpec with Matchers {
  "FixedPointIsReal functions should work for a number of different precisions" - {
    for(binaryPoint <- 0 to 4) {
      s"should work with binary point $binaryPoint" in {
        dsptools.Driver.execute(() => new FixedRing1(16, binaryPoint = binaryPoint)) { c =>
          new FixedRing1Tester(c)
        } should be (true)
      }
    }
  }
}
