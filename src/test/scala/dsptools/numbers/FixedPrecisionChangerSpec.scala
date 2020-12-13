// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3._
import chisel3.experimental.FixedPoint
import dsptools.DspTester
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

//scalastyle:off magic.number

class FixedPrecisionChanger(inWidth: Int, inBinaryPoint: Int, outWidth: Int, outBinaryPoint: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Input(FixedPoint(inWidth.W, inBinaryPoint.BP))
    val out = Output(FixedPoint(outWidth.W, outBinaryPoint.BP))
  })

  val reg = Reg(FixedPoint())
  reg := io.in
  io.out := reg
}

class FixedPointTruncatorTester(c: FixedPrecisionChanger, inValue: Double, outValue: Double) extends DspTester(c) {
  poke(c.io.in, inValue)
  step(1)
  expect(c.io.out, outValue, s"got ${peek(c.io.out)} should have $outValue")
}

class RemoveMantissa(inWidth: Int, inBinaryPoint: Int, outWidth: Int, outBinaryPoint: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Input(FixedPoint(inWidth.W, inBinaryPoint.BP))
    val out = Output(FixedPoint(outWidth.W, 0.BP))
  })

  val reg = Reg(FixedPoint())
  reg := io.in
  io.out := reg//.setBinaryPoint(0)
}

class RemoveMantissaTester(c: RemoveMantissa, inValue: Double, outValue: Double) extends DspTester(c) {
  poke(c.io.in, inValue)
  step(1)
  expect(c.io.out, outValue, s"got ${peek(c.io.out)} should have $outValue")
}

class FixedPrecisionChangerSpec extends AnyFreeSpec with Matchers {
  "assignment of numbers with differing binary points seems to work as I would expect" - {
    "here we assign to a F8.1 from a F8.3" in {
      dsptools.Driver.execute(() => new FixedPrecisionChanger(8, 3, 8, 1)) { c =>
        new FixedPointTruncatorTester(c, 6.875, 6.5)
      } should be (true)
    }
    "here we assign to a F8.1 from a F8.1" - {
      "conversion to fixed point with less precision than poked value rounds up to 7,  IS THIS RIGHT?" in {
        dsptools.Driver.execute(() => new FixedPrecisionChanger(8, 1, 8, 1)) { c =>
          new FixedPointTruncatorTester(c, 6.875, 7.0)
        } should be(true)
      }
    }
    "here we assign to a F10.6 from a F10.3" in {
      dsptools.Driver.execute(() => new FixedPrecisionChanger(10, 3, 10, 6)) { c =>
        new FixedPointTruncatorTester(c, 6.875, 6.875)
      } should be (true)
    }
    "let's try 1/3 just for fun with a big mantissa" - {
      "oops, this works because I built in a fudge factor for double comparison, how should this be done" in {
        dsptools.Driver.execute(() => new FixedPrecisionChanger(64, 58, 64, 16)) { c =>
          new FixedPointTruncatorTester(c, 1.0 / 3.0, 0.3333282470703125)
        } should be(true)
      }
    }
  }

  "removing mantissa can be done" - {
    "by using the setBinaryPoint Method" in {
      dsptools.Driver.execute(() => new RemoveMantissa(12, 4, 8, 0)) { c =>
        new RemoveMantissaTester(c, 3.75, 3.0)
      } should be(true)
    }
  }
}
