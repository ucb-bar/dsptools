// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

//scalastyle:off magic.number

import chisel3._
import chisel3.experimental._
import chisel3.internal.firrtl.IntervalRange
import dsptools.DspTester
import dsptools.numbers.implicits._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class IntervalRing1(val width: Int, val binaryPoint: Int) extends Module {
  val range = IntervalRange(width.W, binaryPoint.BP)
  val io = IO(new Bundle {
    val in = Input(Interval(range))
    val floor = Output(Interval(range))
    val ceil = Output(Interval(range))
    val isWhole = Output(Bool())
    val round = Output(Interval(range))
    val real = Output(DspReal())
  })

  io.floor := io.in.floor()
  io.ceil := io.in.ceil().squeeze(io.ceil)
  io.isWhole := io.in.isWhole()
  io.round := io.in.round()
  io.real := DspReal(0)
}

class IntervalRing1Tester(c: IntervalRing1) extends DspTester(c) {
  val increment: Double = if(c.range.binaryPoint.get == 0) 1.0 else 1.0 / (1 << c.range.binaryPoint.get)
  updatableDspVerbose.withValue(false) {
    for(bd <- BigDecimal(-2.0) to BigDecimal(3.0) by increment) {
      val i = bd.toDouble
      poke(c.io.in, i)

      expect(c.io.floor, breeze.numerics.floor(i), s"floor of $i should be ${breeze.numerics.floor(i)}")
      expect(c.io.ceil, breeze.numerics.ceil(i), s"ceil of $i should be ${breeze.numerics.ceil(i)}")
      expect(c.io.isWhole, breeze.numerics.floor(i) == i , s"isWhole of $i should be ${breeze.numerics.floor(i) == i}")
      expect(c.io.round, breeze.numerics.round(i), s"round of $i should be ${breeze.numerics.round(i)}")
      step(1)
    }
  }
}

/**
  * Shift the inValue right and left, statically and dynamically.
  * @note shiftRight has a constraint that shift amount must be less than width of inValue
  * @param width width of shift target
  * @param binaryPoint the binary point of the shift target
  * @param fixedShiftSize how much to shift the target
  */
class IntervalShifter(val width: Int, val binaryPoint: Int, val fixedShiftSize: Int) extends Module {
  val dynamicShifterWidth = 3

  val io = IO(new Bundle {
    val inValue = Input(Interval(IntervalRange(width.W, binaryPoint.BP)))
    val dynamicShiftValue = Input(UInt(dynamicShifterWidth.W))
    val shiftRightResult: Option[Interval] = if(fixedShiftSize < width) {
      Some(Output(Interval(IntervalRange((width - fixedShiftSize).W, binaryPoint.BP))))
    }
    else {
      None
    }
    val shiftLeftResult = Output(Interval(IntervalRange((width + fixedShiftSize).W, binaryPoint.BP)))
    val dynamicShiftRightResult = Output(Interval(IntervalRange(width.W, binaryPoint.BP)))
    val dynamicShiftLeftResult = Output(
      Interval(IntervalRange((width + (1 << dynamicShifterWidth) - 1).W, binaryPoint.BP))
    )
  })

  io.shiftLeftResult := io.inValue << fixedShiftSize
  io.shiftRightResult.foreach { out =>
    out := (io.inValue >> fixedShiftSize).asInstanceOf[Interval].squeeze(out)
  }
  io.dynamicShiftLeftResult := io.inValue << io.dynamicShiftValue
  io.dynamicShiftRightResult := io.inValue >> io.dynamicShiftValue
}

object IntervalShifter extends App {
  iotesters.Driver.executeFirrtlRepl(Array(), () => new IntervalShifter(8, 4, 3))
}

class IntervalShifterTest(c: IntervalShifter) extends DspTester(c) {
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
    val factor = 1 << c.binaryPoint
    val x = value * factor
    val y = x.toInt.toDouble
    val z = y / factor
    z
  }
  updatableDspVerbose.withValue(false) {

    poke(c.io.dynamicShiftValue, 0)

    val (minSIntValue, maxSIntValue) = firrtl_interpreter.extremaOfSIntOfWidth(c.width)

    val minValue = minSIntValue.toDouble * increment
    val maxValue = maxSIntValue.toDouble * increment

    for(bd <- BigDecimal(minValue) to BigDecimal(maxValue) by increment) {
      val value = bd.toDouble
      poke(c.io.inValue, value)
      expect(c.io.shiftLeftResult, expectedValue(value, left = true, c.fixedShiftSize),
        s"shift left ${c.fixedShiftSize} of $value should be ${expectedValue(value, left = true, c.fixedShiftSize)}")
      c.io.shiftRightResult.foreach { sro =>
        expect(sro, expectedValue(value, left = false, c.fixedShiftSize),
          s"shift right ${c.fixedShiftSize} of $value should be ${expectedValue(value, left = false, c.fixedShiftSize)}")
      }

      step(1)

      for(dynamicShiftValue <- 0 until c.width) {
        poke(c.io.dynamicShiftValue, dynamicShiftValue)
        step(1)
        expect(c.io.dynamicShiftLeftResult, expectedValue(value, left = true, dynamicShiftValue),
          s"dynamic shift left $dynamicShiftValue of $value should " +
            s"be ${expectedValue(value, left = true, dynamicShiftValue)}")
        expect(c.io.dynamicShiftRightResult, expectedValue(value, left = false, dynamicShiftValue),
          s"dynamic shift right $dynamicShiftValue of $value should" +
            s"be ${expectedValue(value, left = false, dynamicShiftValue)}")
      }
    }

  }
}

class IntervalBrokenShifter(n: Int) extends Module {
  val io = IO(new Bundle {
    val i = Input(Interval(range"[-256,256).4"))
    val o = Output(Interval(range"[-256,256).4"))
    val si = Input(SInt(8.W))
    val so = Output(SInt(8.W))
  })
  io.o := io.i >> n
  io.so := io.si >> n
}

class IntervalBrokenShifterTester(c: IntervalBrokenShifter) extends DspTester(c) {
  updatableDspVerbose.withValue(false) {
    poke(c.io.i, 1.5)
    peek(c.io.o)
    poke(c.io.si, 6)
    peek(c.io.so)
  }
}

class IntervalSpec extends AnyFreeSpec with Matchers {
  "Interval numbers should work properly for the following mathematical type functions" - {
//    for (backendName <- Seq("verilator")) {
    for (backendName <- Seq("treadle")) {
//    for (backendName <- Seq("firrtl")) {
//    for (backendName <- Seq("treadle", "verilator")) {
      s"The ring family run with the $backendName simulator" - {
        for (binaryPoint <- 0 to 4 by 2) {
          s"should work, with binaryPoint $binaryPoint" in {
            dsptools.Driver.execute(
              () => new IntervalRing1(16, binaryPoint),
              Array(
                "--backend-name", backendName,
                "--target-dir", s"test_run_dir/interval-ring-tests-$binaryPoint.BP"
              )
            ) { c =>
              new IntervalRing1Tester(c)
            } should be(true)
          }
        }
      }

      s"The shift family when run with the $backendName simulator" - {
        val defaultWidth = 8
        for {
          binaryPoint <- Set(0, 1, 1) ++
            (defaultWidth - 1 to defaultWidth + 1) ++
            (defaultWidth * 2 - 1 to defaultWidth * 2 + 1)
          fixedShiftSize <- Set(0, 1, 2) ++ (defaultWidth - 1 to defaultWidth + 1)
        } {
          s"should work with binary point $binaryPoint, with shift $fixedShiftSize " in {
            dsptools.Driver.execute(
              () => new IntervalShifter(width = 8, binaryPoint = binaryPoint, fixedShiftSize = fixedShiftSize),
              Array(
                "--backend-name", backendName,
                "--target-dir", s"test_run_dir/interval-shift-test-$fixedShiftSize-$binaryPoint.BP"
              )
            ) { c =>
              new IntervalShifterTest(c)
            } should be(true)
          }
        }
      }
    }
  }
}
