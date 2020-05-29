// See LICENSE for license details.

package dsptools.intervals.tests

import chisel3._
import chisel3.experimental._
import firrtl.ir.Closed
import generatortools.io.CustomBundle
import generatortools.testing.TestModule
import org.scalatest.{Matchers, FlatSpec}
import chisel3.internal.firrtl.IntervalRange
import dsptools.DspTester
import dsptools.intervals.IAUtility

class IAOverflow(r: IATestParams) extends Module {
  val Seq(aRange, bRange, cRange, dRange, _) = r.ranges
  val Seq(_, _, bp, _, _) = r.bps
  val selRange = range"[0, 1]"

  val inputNames = Seq("a", "b", "c", "d")

  val io = IO(new Bundle {
    val a = Input(Interval(aRange))
    val b = Input(Interval(bRange))
    val c = Input(Interval(cRange))
    val d = Input(Interval(dRange))
    val sel = Input(Interval(selRange))

    val clipShrink = IATest.outputs(inputNames, bp)
    val clipExpand = IATest.outputs(inputNames, bp)
    // Shift range right
    val clipShiftRight = IATest.outputs(inputNames, bp)
    // Shift range left
    val clipShiftLeft = IATest.outputs(inputNames, bp)
    // S/UInt-based clip
    val clipX = IATest.outputs(inputNames, bp)

    val wrapShrink = IATest.outputs(inputNames, bp)
    val wrapExpand = IATest.outputs(inputNames, bp)
    // Shift range right
    val wrapShiftRight = IATest.outputs(inputNames, bp)
    // Shift range left
    val wrapShiftLeft = IATest.outputs(inputNames, bp)
    // S/UInt-based wrap
    val wrapX = IATest.outputs(inputNames, bp)

    // Beyond this, can't use mux for wrap
    val wrapLowerEdge = IATest.outputs(inputNames, bp)
    val wrapUpperEdge = IATest.outputs(inputNames, bp)

    // Mux (wrap, clip) behavior for shift range right
    val rangeShiftRight = IATest.outputs(inputNames, bp)

  })

  val ins = Seq(io.a, io.b, io.c, io.d)

  ins.zipWithIndex foreach { case (i, idx) =>
    // New ranges after wrap/clip
    val shrink = IAUtility.expandBy(i.range, -1)
    val expand = IAUtility.expandBy(i.range, 1)
    val shiftRight = IAUtility.shiftRightBy(i.range, 1)
    val shiftLeft = IAUtility.shiftRightBy(i.range, -1)

    val containsNeg = IAUtility.containsNegative(i.range)
    val halfWidth = IAUtility.getIntWidth(i.range) - 1

    require(halfWidth >=1)

    val res = 1.toDouble / (1 << i.binaryPoint.get)
    // wrap lower edge
    val lowerEdge = IAUtility.shiftRightBy(i.range, IAUtility.getRange(i.range).toDouble + res)
    // wrap upper edge
    val upperEdge = IAUtility.shiftRightBy(i.range, -(IAUtility.getRange(i.range).toDouble + res))

    val shrinkInterval = Wire(Interval(shrink))

    io.clipShrink.seq(idx) := i.clip(shrinkInterval)
    io.clipExpand.seq(idx) := i.clip(expand)
    io.clipShiftRight.seq(idx) := i.clip(shiftRight)
    io.clipShiftLeft.seq(idx) := i.clip(shiftLeft)

    io.wrapShrink.seq(idx) := i.wrap(shrinkInterval)
    io.wrapExpand.seq(idx) := i.wrap(expand)
    io.wrapShiftRight.seq(idx) := i.wrap(shiftRight)
    io.wrapShiftLeft.seq(idx) := i.wrap(shiftLeft)

    io.wrapLowerEdge.seq(idx) := i.wrap(lowerEdge)
    io.wrapUpperEdge.seq(idx) := i.wrap(upperEdge)

    if (containsNeg) {
      val sint = Wire(SInt(halfWidth.W))
      io.wrapX.seq(idx) := i.wrap(sint)
      io.clipX.seq(idx) := i.clip(sint)
    }
    else {
      val uint = Wire(UInt(halfWidth.W))
      io.wrapX.seq(idx) := i.wrap(uint)
      io.clipX.seq(idx) := i.clip(uint)
    }

    io.rangeShiftRight.seq(idx) := Mux(io.sel > 0.I, i.clip(shiftRight), i.wrap(shiftRight))
  }
}

class IAOverflowSpec extends FlatSpec with Matchers {

  behavior of "IA: wrap, clip"

  it should "properly infer [_, _] ranges and compute" in {
    dsptools.Driver.execute(() => new TestModule(() => new IAOverflow(IATest.cc)), IATest.options("cc")) {
      c => new IAOverflowTester(c)
    } should be (true)
  }

  it should "properly infer [_, _) ranges and compute" in {
    dsptools.Driver.execute(() => new TestModule(() => new IAOverflow(IATest.co)), IATest.options("co")) {
      c => new IAOverflowTester(c)
    } should be (true)
  }

  it should "properly infer (_, _] ranges and compute" in {
    dsptools.Driver.execute(() => new TestModule(() => new IAOverflow(IATest.oc)), IATest.options("oc")) {
      c => new IAOverflowTester(c)
    } should be (true)
  }

  it should "properly infer (_, _) ranges and compute" in {
    dsptools.Driver.execute(() => new TestModule(() => new IAOverflow(IATest.oo)), IATest.options("oo")) {
      c => new IAOverflowTester(c)
    } should be (true)
  }

}

class IAOverflowTester(testMod: TestModule[IAOverflow]) extends DspTester(testMod) {
  val tDut = testMod.dut
  val inputNames = tDut.inputNames :+ "sel"

  val Seq(as, bs, cs, ds, sels) = inputNames map {str =>
    val possibleVals = testMod.getDutIO(str).asInstanceOf[Interval].range.getPossibleValues
    println(s"$str min: ${possibleVals.min}, $str max: ${possibleVals.max}")
    possibleVals
  }

  def clip(i: Double, range: IntervalRange): BigDecimal = clip(BigDecimal(i), range)
  def clip(i: BigDecimal, range: IntervalRange): BigDecimal = {
    val max = IAUtility.getMax(range)
    val min = IAUtility.getMin(range)
    if (i > max) max
    else if (i < min) min
    else i
  }

  // "Simple" mod: Doesn't work if double is outside of range expanded
  def wrap(i: Double, range: IntervalRange): BigDecimal = wrap(BigDecimal(i), range)
  def wrap(i: BigDecimal, range: IntervalRange): BigDecimal = {
    val max = IAUtility.getMax(range)
    val min = IAUtility.getMin(range)
    val width = IAUtility.getRange(range)
    val res = 1.toDouble / (1 << range.binaryPoint.get)
    require(i <= max + width + res && i >= min - width - res, "i out of bounds")
    if (i > max) i - max - res + min
    else if (i < min) max - (min - i) + res
    else i
  }

  def getO(name: String) = {
    testMod.getIO(name).asInstanceOf[CustomBundle[Data]].seq
  }

  val maxLen = Seq(as.length, bs.length, cs.length, ds.length).max
  val Seq(asT, bsT, csT, dsT) = Seq(as, bs, cs, ds).map(x => x.padTo(maxLen, x.max))
  val idxs = 0 until maxLen

  for (idx <- idxs; sel <- sels) {

    val inVals = Seq(asT(idx), bsT(idx), csT(idx), dsT(idx), sel)

    inputNames.zip(inVals) foreach { case (name, value) =>
      poke(testMod.getIO(name), value)
    }

    // TODO: Should not be calculating static things every loop
    tDut.ins.zipWithIndex foreach { case (i, idx) =>
      val shrink = IAUtility.expandBy(i.range, -1)
      val expand = IAUtility.expandBy(i.range, 1)
      val shiftRight = IAUtility.shiftRightBy(i.range, 1)
      val shiftLeft = IAUtility.shiftRightBy(i.range, -1)

      val containsNeg = IAUtility.containsNegative(i.range)
      val halfWidth = IAUtility.getIntWidth(i.range) - 1

      val res = 1.toDouble / (1 << i.binaryPoint.get)
      val lowerEdge = IAUtility.shiftRightBy(i.range, IAUtility.getRange(i.range).toDouble + res)
      val upperEdge = IAUtility.shiftRightBy(i.range, -(IAUtility.getRange(i.range).toDouble + res))

      val xRange = if (containsNeg) {
        val outside = 1 << (halfWidth - 1)
        IntervalRange(Closed(-outside), Closed(outside - 1), i.range.binaryPoint)
      }
      else
        IntervalRange(Closed(0), Closed((1 << halfWidth) - 1), i.range.binaryPoint)

      expect(getO("clipShrink")(idx), clip(inVals(idx), shrink))
      expect(getO("clipExpand")(idx), clip(inVals(idx), expand))
      expect(getO("clipShiftRight")(idx), clip(inVals(idx), shiftRight))
      expect(getO("clipShiftLeft")(idx), clip(inVals(idx), shiftLeft))

      expect(getO("wrapShrink")(idx), wrap(inVals(idx), shrink))
      expect(getO("wrapExpand")(idx), wrap(inVals(idx), expand))
      expect(getO("wrapShiftRight")(idx), wrap(inVals(idx), shiftRight))
      expect(getO("wrapShiftLeft")(idx), wrap(inVals(idx), shiftLeft))

      expect(getO("wrapLowerEdge")(idx), wrap(inVals(idx), lowerEdge))
      expect(getO("wrapUpperEdge")(idx), wrap(inVals(idx), upperEdge))

      expect(getO("wrapX")(idx), wrap(inVals(idx), xRange))
      expect(getO("clipX")(idx), clip(inVals(idx), xRange))

      expect(
        getO("rangeShiftRight")(idx),
        if (sel == 1) clip(inVals(idx), shiftRight) else wrap(inVals(idx), shiftRight)
      )
    }

  }
}