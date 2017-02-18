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
  for(i <- -2.0 to 3.0 by increment) {
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

object FixedPointShifter extends App {
  iotesters.Driver.executeFirrtlRepl(Array(), () => new FixedPointShifter(8, 4, 3))
}

class FixedPointShiftTester(c: FixedPointShifter) extends DspTester(c) {
  val increment: Double = 1.0 / (1 << c.binaryPoint)

  def expectedValue(value: Double, left: Boolean, shift: Int): Double = {
    val factor = 1 << c.binaryPoint
    val x = value * factor
    val y = x.toInt
    val z = if(left) y << shift else y >> shift
    val w = z.toDouble / factor
    w
  }

  def truncate(value: Double): Double = {
    val coeff = 1 << c.binaryPoint
    val x = value * coeff
    val y = x.toInt.toDouble
    val z = y / coeff
    z
  }
  poke(c.io.dynamicShiftValue, 0)

  private val (minSIntValue, maxSIntValue) = firrtl_interpreter.extremaOfSIntOfWidth(c.width)

  private val minValue = minSIntValue.toDouble * increment
  private val maxValue = maxSIntValue.toDouble * increment

  for(value <- minValue to maxValue by increment) {
    poke(c.io.inValue, value)
    expect(c.io.shiftLeftResult, expectedValue(value, left = true, c.fixedShiftSize),
      s"shift left ${c.fixedShiftSize} of $value should be ${expectedValue(value, left = true, c.fixedShiftSize)}")
    expect(c.io.shiftRightResult, expectedValue(value, left = false, c.fixedShiftSize),
      s"shift right ${c.fixedShiftSize} of $value should be ${expectedValue(value, left = false, c.fixedShiftSize)}")

    step(1)

    for(dynamicShiftValue <- 0 until c.width) {
      poke(c.io.dynamicShiftValue, dynamicShiftValue)
      step(1)
      expect(c.io.dynamicShiftLeftResult, expectedValue(value, left = true, dynamicShiftValue),
        s"dynamic shift left $dynamicShiftValue of $value should " +
          "be ${expectedValue(value, left = true, dynamicShiftValue)}")
      expect(c.io.dynamicShiftRightResult, expectedValue(value, left = false, dynamicShiftValue),
        s"dynamic shift right $dynamicShiftValue of $value should" +
          s"be ${expectedValue(value, left = false, dynamicShiftValue)}")
    }
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
        s"should work with binary point $binaryPoint, for positives even when binaryPoint > width" in {
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
