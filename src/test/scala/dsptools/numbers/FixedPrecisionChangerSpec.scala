// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import chisel3.core.{Bundle, Module}
import dsptools.DspTester
import org.scalatest.{Matchers, FreeSpec}

//scalastyle:off magic.number

class FixedPrecisionChanger(inWidth: Int, inBinaryPoint: Int, outWidth: Int, outBinaryPoint: Int) extends Module {
  val io = new Bundle {
    val in  = FixedPoint(INPUT, inWidth, inBinaryPoint)
    val out = FixedPoint(OUTPUT, outWidth, outBinaryPoint)
  }

  val reg = Reg(FixedPoint())
  reg := io.in
  io.out := reg
}

class FixedPointTruncatorTester(c: FixedPrecisionChanger, inValue: Double, outValue: Double) extends DspTester(c) {
  dspPoke(c.io.in, inValue)
  step(1)
  dspExpect(c.io.out, outValue, s"got ${dspPeekDouble(c.io.out)} should have $outValue")
}

class FixedPrecisionChangerSpec extends FreeSpec with Matchers {
  "assignment of numbers with differing binary points seems to work as I would expect" - {
    "here we assign to a F8.1 from a F8.3" in {
      chisel3.iotesters.Driver(() => new FixedPrecisionChanger(8, 3, 8, 1)) { c=>
        new FixedPointTruncatorTester(c, 6.875, 6.5)
      } should be (true)
    }
    "here we assign to a F8.1 from a F8.1" - {
      "conversion to fixed point with less precision rounds up to 7,  IS THIS RIGHT?" in {
        chisel3.iotesters.Driver(() => new FixedPrecisionChanger(8, 1, 8, 1)) { c =>
          new FixedPointTruncatorTester(c, 6.875, 7.0)
        } should be(true)
      }
    }
    "here we assign to a F8.6 from a F8.3" in {
      chisel3.iotesters.Driver(() => new FixedPrecisionChanger(8, 3, 8, 6)) { c=>
        new FixedPointTruncatorTester(c, 6.875, 6.875)
      } should be (true)
    }
    "let's try 1/3 just for fun with a big mantissa" - {
      "oops, this works because I built in a fudge factor for double comparison, how should this be done" in {
        chisel3.iotesters.Driver(() => new FixedPrecisionChanger(64, 58, 64, 16)) { c =>
          new FixedPointTruncatorTester(c, 1.0 / 3.0, 1.0 / 3.0)
        } should be(true)
      }
    }
  }
}
