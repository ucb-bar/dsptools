// See LICENSE for license details.

package dsptools.numbers

//scalastyle:off magic.number

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
  }
}

class FixedPointShifter(val width: Int, val binaryPoint: Int, val fixedShiftSize: Int) extends Module {
  val dynamicShifterWidth = 3

  val io = IO(new Bundle {
    val inValue = Input(FixedPoint(width.W, binaryPoint.BP))
    val dynamicShiftValue = Input(UInt(dynamicShifterWidth.W))
    val shiftRightResult = Output(FixedPoint((width - fixedShiftSize).W, binaryPoint.BP))
    val shiftLeftResult = Output(FixedPoint((width + fixedShiftSize).W, binaryPoint.BP))
    val dynamicShiftRightResult = Output(FixedPoint(width.W, binaryPoint.BP))
    val dynamicShiftLeftResult = Output(FixedPoint((width + (1 << dynamicShifterWidth) - 1).W, binaryPoint.BP))
  })

  io.shiftLeftResult := io.inValue << fixedShiftSize
  io.shiftRightResult := io.inValue >> fixedShiftSize
  io.dynamicShiftLeftResult := io.inValue << io.dynamicShiftValue
  io.dynamicShiftRightResult := io.inValue >> io.dynamicShiftValue
}

class FixedPointShiftTester(c: FixedPointShifter) extends DspTester(c) {
  val increment: Double = if(c.binaryPoint == 0) 1.0 else 1.0 / (1 << c.binaryPoint)
  println(s"FixedPointShiftTester increment is $increment, fixedShiftValue is ${c.fixedShiftSize}")
  val shiftCoefficient = 1 << c.fixedShiftSize

  poke(c.io.dynamicShiftValue, 0)

  for(value <- -2.0 to 3.0 by increment) {
    println(s"Testing value $value")

    poke(c.io.inValue, value)
    expect(c.io.shiftLeftResult, value * shiftCoefficient,
      s"shift left of $value should be ${value * shiftCoefficient}")
    expect(c.io.shiftRightResult, value / shiftCoefficient,
      s"shift right of $value should be ${value / shiftCoefficient}")

    step(1)
  }
}

class FixedPointSpec extends FreeSpec with Matchers {
  "FixedPoint numbers should work properly for the following mathematical type functions" - {
    "The ring family" - {
      for (binaryPoint <- 0 to 4) {
        s"should work, with binaryPoint $binaryPoint" in {
          dsptools.Driver.execute(() => new FixedRing1(16, binaryPoint = binaryPoint)) { c =>
            new FixedRing1Tester(c)
          } should be(true)
        }
      }
    }

    "The shift family" - {
      for (binaryPoint <- 0 to 16 by 2) {
        s"should work with binary point $binaryPoint" in {
          dsptools.Driver.execute(
            () => new FixedPointShifter(8, binaryPoint = binaryPoint, fixedShiftSize = 2),
            Array("-tdb", "2")
          ) { c =>
            new FixedPointShiftTester(c)
          } should be(true)
        }
      }
    }
  }


}
