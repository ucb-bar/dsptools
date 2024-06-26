// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

//scalastyle:off magic.number

import chisel3.testers.BasicTester
import chisel3._
import fixedpoint._
import dsptools.numbers.implicits._
import org.scalatest.freespec.AnyFreeSpec
import chiseltest._
import chiseltest.iotesters._
import dsptools.misc.PeekPokeDspExtensions
import org.scalatest.propspec.AnyPropSpec

class FixedRing1(val width: Int, val binaryPoint: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(FixedPoint(width.W, binaryPoint.BP))
    val floor = Output(FixedPoint(width.W, binaryPoint.BP))
    val ceil = Output(FixedPoint(width.W, binaryPoint.BP))
    val isWhole = Output(Bool())
    val round = Output(FixedPoint(width.W, binaryPoint.BP))
    val real = Output(DspReal())
  })

  io.floor := io.in.floor
  io.ceil := io.in.ceil
  io.isWhole := io.in.isWhole
  io.round := io.in.round
  io.real := DspReal(0)
}

class FixedRing1Tester(c: FixedRing1) extends PeekPokeTester(c) with PeekPokeDspExtensions {
  val increment: Double = if (c.binaryPoint == 0) 1.0 else 1.0 / (1 << c.binaryPoint)
  for (i <- (BigDecimal(-2.0) to 3.0 by increment).map(_.toDouble)) {
    poke(c.io.in, i)

    expect(c.io.floor, breeze.numerics.floor(i), s"floor of $i should be ${breeze.numerics.floor(i)}")
    expect(c.io.ceil, breeze.numerics.ceil(i), s"ceil of $i should be ${breeze.numerics.ceil(i)}")
    expect(c.io.isWhole, breeze.numerics.floor(i) == i, s"isWhole of $i should be ${breeze.numerics.floor(i) == i}")
    expect(c.io.round, breeze.numerics.round(i).toDouble, s"round of $i should be ${breeze.numerics.round(i)}")
    step(1)
  }
}

/**
  * Shift the inValue right and left, statically and dynamically.
  * @note shiftRight has a constraint that shift amount must be less than width of inValue
  * @param width width of shift target
  * @param binaryPoint the binary point of the shift target
  * @param fixedShiftSize how much to shift the target
  */
class FixedPointShifter(val width: Int, val binaryPoint: Int, val fixedShiftSize: Int) extends Module {
  val dynamicShifterWidth = 3

  val io = IO(new Bundle {
    val inValue = Input(FixedPoint(width.W, binaryPoint.BP))
    val dynamicShiftValue = Input(UInt(dynamicShifterWidth.W))
    val shiftRightResult: Option[FixedPoint] = if (fixedShiftSize < width) {
      Some(Output(FixedPoint((width - fixedShiftSize).W, binaryPoint.BP)))
    } else {
      None
    }
    val shiftLeftResult = Output(FixedPoint((width + fixedShiftSize).W, binaryPoint.BP))
    val dynamicShiftRightResult = Output(FixedPoint(width.W, binaryPoint.BP))
    val dynamicShiftLeftResult = Output(FixedPoint((width + (1 << dynamicShifterWidth) - 1).W, binaryPoint.BP))
  })

  io.shiftLeftResult := io.inValue << fixedShiftSize
  io.shiftRightResult.foreach { out =>
    out := io.inValue >> fixedShiftSize
  }
  io.dynamicShiftLeftResult := io.inValue << io.dynamicShiftValue
  io.dynamicShiftRightResult := io.inValue >> io.dynamicShiftValue
}

class FixedPointShiftTester(c: FixedPointShifter) extends PeekPokeTester(c) with PeekPokeDspExtensions {
  val increment: Double = 1.0 / (1 << c.binaryPoint)

  def expectedValue(value: Double, left: Boolean, shift: Int): Double = {
    val factor = 1 << c.binaryPoint
    val x = value * factor
    val y = x.toInt
    val z = if (left) y << shift else y >> shift
    val w = z.toDouble / factor
    w
  }

  def truncate(value: Double): Double = {
    val factor = 1 << c.binaryPoint
    val x = value * factor
    val y = x.toInt.toDouble
    val z = y / factor
    z
  }

  def extremaOfSIntOfWidth(width: Int): (BigInt, BigInt) = {
    val nearestPowerOf2 = BigInt("1" + ("0" * (width - 1)), 2)
    (-nearestPowerOf2, nearestPowerOf2 - 1)
  }

  poke(c.io.dynamicShiftValue, 0)

  val (minSIntValue, maxSIntValue) = extremaOfSIntOfWidth(c.width)

  val minValue = minSIntValue.toDouble * increment
  val maxValue = maxSIntValue.toDouble * increment

  for (value <- (BigDecimal(minValue) to maxValue by increment).map(_.toDouble)) {
    poke(c.io.inValue, value)
    expect(
      c.io.shiftLeftResult,
      expectedValue(value, left = true, c.fixedShiftSize),
      s"shift left ${c.fixedShiftSize} of $value should be ${expectedValue(value, left = true, c.fixedShiftSize)}"
    )
    c.io.shiftRightResult.foreach { sro =>
      expect(
        sro,
        expectedValue(value, left = false, c.fixedShiftSize),
        s"shift right ${c.fixedShiftSize} of $value should be ${expectedValue(value, left = false, c.fixedShiftSize)}"
      )
    }

    step(1)

    for (dynamicShiftValue <- 0 until c.width) {
      poke(c.io.dynamicShiftValue, dynamicShiftValue)
      step(1)
      expect(
        c.io.dynamicShiftLeftResult,
        expectedValue(value, left = true, dynamicShiftValue),
        s"dynamic shift left $dynamicShiftValue of $value should " +
          s"be ${expectedValue(value, left = true, dynamicShiftValue)}"
      )
      expect(
        c.io.dynamicShiftRightResult,
        expectedValue(value, left = false, dynamicShiftValue),
        s"dynamic shift right $dynamicShiftValue of $value should" +
          s"be ${expectedValue(value, left = false, dynamicShiftValue)}"
      )
    }
  }

}

class BrokenShifter(n: Int) extends Module {
  val io = IO(new Bundle {
    val i = Input(FixedPoint(8.W, 4.BP))
    val o = Output(FixedPoint(8.W, 4.BP))
    val si = Input(SInt(8.W))
    val so = Output(SInt(8.W))
  })
  io.o := io.i >> n
  io.so := io.si >> n
}

class BrokenShifterTester(c: BrokenShifter) extends PeekPokeTester(c) with PeekPokeDspExtensions {
  poke(c.io.i, 1.5)
  peek(c.io.o)
  poke(c.io.si, 6)
  peek(c.io.so)
}

class FixedPointSpec extends AnyFreeSpec with ChiselScalatestTester {
  "FixedPoint numbers should work properly for the following mathematical type functions" - {
    //    for (backendName <- Seq("verilator")) {
    for (backendName <- Seq("default", "verilator")) {
      val annos = if (backendName == "verilator") Seq(VerilatorBackendAnnotation) else Seq()

      s"The ring family run with the $backendName simulator" - {
        for (binaryPoint <- 0 to 4 by 2) {
          s"should work, with binaryPoint $binaryPoint" in {
            test(new FixedRing1(16, binaryPoint = binaryPoint))
              .withAnnotations(annos)
              .runPeekPoke(new FixedRing1Tester(_))
          }
        }
      }

      s"The shift family when run with the $backendName simulator" - {
        val defaultWidth = 8
        for {
          binaryPoint <-
            Set(0, 1, 1) ++
              (defaultWidth - 1 to defaultWidth + 1) ++
              (defaultWidth * 2 - 1 to defaultWidth * 2 + 1)
          fixedShiftSize <- Set(0, 1, 2) ++ (defaultWidth - 1 to defaultWidth + 1)
        } {
          s"should work with binary point $binaryPoint, with shift $fixedShiftSize " in {
            test(new FixedPointShifter(width = 8, binaryPoint = binaryPoint, fixedShiftSize = fixedShiftSize))
              .withAnnotations(annos)
              .runPeekPoke(new FixedPointShiftTester(_))
          }
        }
      }

      //TODO: This error does not seem to be caught at this time.  Firrtl issue #450
      s"shifting by too big a number causes error with $backendName" ignore {
        for (shiftSize <- 8 to 10) {
          test(new BrokenShifter(n = shiftSize))
            .withAnnotations(annos)
            .runPeekPoke(new BrokenShifterTester(_))
        }
      }

    }
  }
}

class FixedPointChiselSpec extends AnyPropSpec with ChiselScalatestTester {
  property("asReal shold work") {
    test(new BasicTester {
      val x = FixedPoint.fromDouble(13.5, 16.W, 4.BP)

      val y = x.asReal

      chisel3.assert(y === DspReal(13.5))

      stop()
    })
      .withAnnotations(Seq(VerilatorBackendAnnotation))
      .runUntilStop()
  }
}
