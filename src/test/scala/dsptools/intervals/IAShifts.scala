// See LICENSE for license details.

package dsptools.intervals.tests

import java.math.{MathContext, RoundingMode}

import chisel3._
import chisel3.experimental._
import generatortools.io.CustomBundle
import generatortools.testing.TestModule
import org.scalatest.{FlatSpec, Matchers}
import chisel3.internal.firrtl.IntervalRange
import dsptools.DspTester

// TODO: Check negative binary points (bpset, bpshr, bpshl)
class IAShifts(r: IATestParams) extends Module {
  val Seq(aRange, bRange, cRange, dRange, _) = r.ranges
  val selRange = range"[0, 1]"
  val Seq(zbp, bp, shbp, _, _) = r.bps

  val inputNames = Seq("a", "b", "c", "d")

  val shlAmt = shbp + 1
  val shrEdgeAmt = bp + 3
  val shrAmt = bp - 3
  val shiftRange = 2

  val bpDelta = 2

  val io = IO(new Bundle {
    val a = Input(Interval(aRange))
    val b = Input(Interval(bRange))
    val c = Input(Interval(cRange))
    val d = Input(Interval(dRange))
    val sel = Input(Interval(selRange))

    def createAmts(ins: Seq[Interval], names: Seq[String], delta: Int) = {
      val outs = ins.map(i => Input(UInt(range"[0, ${i.getWidth - delta}]")))
      new CustomBundle(names.zip(outs): _*)
    }

    // Legal dynamic right shift amounts
    val legal = createAmts(Seq(a, b, c, d), inputNames, shiftRange)
    val outOfLegal = createAmts(Seq(a, b, c, d), inputNames, -shiftRange)

    val dshl = IATest.outputs(inputNames, shbp)
    val dshrLegal = IATest.outputs(inputNames, shbp)
    val dshrOver = IATest.outputs(inputNames, shbp)

    // Through mux shl, shr (more than width)
    val sh = IATest.outputs(inputNames, shbp)

    // shr (more than width)
    val shrEdge = IATest.outputs(inputNames, shbp)

    // shr (less than width)
    val shr = IATest.outputs(inputNames, shbp)

    val dsh = IATest.outputs(inputNames, shbp)

    // More BP than current
    val bpsetMore = IATest.outputs(inputNames, shbp)
    val bpsetLess = IATest.outputs(inputNames, shbp)
    val bpset0 = IATest.outputs(inputNames, shbp)

    val bpshl = IATest.outputs(inputNames, shbp)

    val bpshrToZero = IATest.outputs(inputNames, shbp)
    val bpshr = IATest.outputs(inputNames, shbp)

    val bpSet0Shl = IATest.outputs(inputNames, shbp)
    val bpSetMoreShr = IATest.outputs(inputNames, shbp)

  })

  val ins = Seq(io.a, io.b, io.c, io.d)

  ins.zipWithIndex foreach { case (i, idx) =>
    io.sh.seq(idx) := Mux(io.sel > 0.I, i << shlAmt, i >> shrEdgeAmt)
    io.shrEdge.seq(idx) := i >> shrEdgeAmt
    io.shr.seq(idx) := i >> shrAmt

    io.dshl.seq(idx) := i << io.legal.seq(idx)
    io.dshrLegal.seq(idx) := i >> io.legal.seq(idx)
    io.dshrOver.seq(idx) := i >> io.outOfLegal.seq(idx)

    io.dsh.seq(idx) := Mux(io.sel > 0.I, i << io.legal.seq(idx), i >> io.legal.seq(idx))

    io.bpsetMore.seq(idx) := i.setPrecision(i.binaryPoint.get + bpDelta)
    io.bpsetLess.seq(idx) := i.setPrecision(Seq(i.binaryPoint.get - bpDelta, 0).max)
    io.bpset0.seq(idx) := i.setPrecision(0)
    io.bpshl.seq(idx) := i.increasePrecision(bpDelta)
    io.bpshrToZero.seq(idx) := i.decreasePrecision(i.binaryPoint.get)
    io.bpshr.seq(idx) := i.decreasePrecision(Seq(bpDelta, i.binaryPoint.get).min)

    io.bpSet0Shl.seq(idx) := Mux(io.sel > 0.I, i.setPrecision(0), i.increasePrecision(bpDelta))
    io.bpSetMoreShr.seq(idx) :=
      Mux(
        io.sel > 0.I,
        i.setPrecision(i.binaryPoint.get + bpDelta), i.decreasePrecision(Seq(bpDelta, i.binaryPoint.get).min)
      )
  }

}

class IAShiftsSpec extends FlatSpec with Matchers {

  behavior of "IA: (d) >>, (d) <<, bpsl, bpshr, bpset"

  it should "properly infer [_, _] ranges and compute" in {
    dsptools.Driver.execute(() => new TestModule(() => new IAShifts(IATest.cc)), IATest.options("cc")) {
      c => new IAShiftsTester(c)
    } should be (true)
  }

  it should "properly infer [_, _) ranges and compute" in {
    dsptools.Driver.execute(() => new TestModule(() => new IAShifts(IATest.co)), IATest.options("co")) {
      c => new IAShiftsTester(c)
    } should be (true)
  }

  it should "properly infer (_, _] ranges and compute" in {
    dsptools.Driver.execute(() => new TestModule(() => new IAShifts(IATest.oc)), IATest.options("oc")) {
      c => new IAShiftsTester(c)
    } should be (true)
  }

  it should "properly infer (_, _) ranges and compute" in {
    dsptools.Driver.execute(() => new TestModule(() => new IAShifts(IATest.oo)), IATest.options("oo")) {
      c => new IAShiftsTester(c)
    } should be (true)
  }
}

class IAShiftsTester(testMod: TestModule[IAShifts]) extends DspTester(testMod) {
  val tDut = testMod.dut
  val bpDelta = tDut.bpDelta
  val inputNames = tDut.inputNames :+ "sel"

  val Seq(as, bs, cs, ds, sels) = inputNames map {str =>
    val possibleVals = testMod.getDutIO(str).asInstanceOf[Interval].range.getPossibleValues
    println(s"$str min: ${possibleVals.min}, $str max: ${possibleVals.max}")
    possibleVals
  }

  val maxShiftAmt = (1 << tDut.io.outOfLegal.seq.map(_.getWidth).max) - 1
  val dynShiftAmts = 0 to maxShiftAmt

  def shl(i: Double, amt: Int): BigDecimal = shl(BigDecimal(i), amt)
  def shl(i: BigDecimal, amt: Int): BigDecimal = i * math.pow(2, amt)

  // Remove LSBs = "floor" behavior to resolution -- does not grow in precision, so lose significant bits
  def shr(i: Double, bp: Int, amt: Int): BigDecimal = shr(BigDecimal(i), bp, amt)
  def shr(i: BigDecimal, bp: Int, amt: Int): BigDecimal = {
    i.round(new MathContext(1, RoundingMode.FLOOR));
//    math.floor(i * math.pow(2, bp - amt)) * math.pow(2, -bp)
  }

  def bpset(i: Double, bp: Int, origbp: Int): BigDecimal = bpset(BigDecimal(i), bp, origbp)
  def bpset(i: BigDecimal, bp: Int, origbp: Int): BigDecimal = {
    if (origbp < bp) {
      i
    } else {
      shr(i, origbp, origbp - bp) * (1 << (origbp - bp))
    }
  }

  def bpshr(i: Double, bp: Int, amt: Int): BigDecimal = bpshr(BigDecimal(i), bp, amt)
  def bpshr(i: BigDecimal, bp: Int, amt: Int): BigDecimal = {
    shr(i, bp, amt) * (1 << amt)
  }

  val maxLen = Seq(as.length, bs.length, cs.length, ds.length).max
  val Seq(asT, bsT, csT, dsT) = Seq(as, bs, cs, ds).map(x => x.padTo(maxLen, x.max))
  val idxs = 0 until maxLen

  for (idx <- idxs; sel <- sels; dynShiftAmt <- dynShiftAmts) {
    val inVals = Seq(asT(idx), bsT(idx), csT(idx), dsT(idx), sel)

    inputNames.zip(inVals) foreach { case (name, value) =>
      poke(testMod.getIO(name), value)
    }

    def pokeShiftAmt(kind: String, amt: Int): Seq[Int] = {
      testMod.getIO(kind).asInstanceOf[CustomBundle[Data]].seq.map { case u =>
        val pokeVal = amt % (1 << u.asInstanceOf[UInt].getWidth)
        poke(u, pokeVal)
        pokeVal
      }
    }

    val legalShiftAmts = pokeShiftAmt("legal", dynShiftAmt)
    val outOfLegalShiftAmts = pokeShiftAmt("outOfLegal", dynShiftAmt)

    val Seq(shs, shrEdges, shrs, dshls, dshrLegals, dshrOvers, dshs,
      bpsetMores, bpsetLesses, bpset0s, bpshls, bpshrToZeros, bpshrs, bpSet0Shls, bpSetMoreShrs
    ) =
      Seq("sh", "shrEdge", "shr", "dshl", "dshrLegal", "dshrOver", "dsh",
          "bpsetMore", "bpsetLess", "bpset0", "bpshl", "bpshrToZero", "bpshr", "bpSet0Shl", "bpSetMoreShr").map(
        testMod.getIO(_).asInstanceOf[CustomBundle[Data]].seq
      )

    inputNames.init.zipWithIndex.zip(inVals) foreach { case ((inName, idx), inVal) =>
      val ibp = testMod.getDutIO(inName).asInstanceOf[Interval].binaryPoint.get
      expect(shs(idx), if (sel == 1) shl(inVal, tDut.shlAmt) else shr(inVal, ibp, tDut.shrEdgeAmt))
      expect(shrEdges(idx), shr(inVal, ibp, tDut.shrEdgeAmt))
      expect(shrs(idx), shr(inVal, ibp, tDut.shrAmt))

      expect(dshls(idx), shl(inVal, legalShiftAmts(idx)))
      expect(dshrLegals(idx), shr(inVal, ibp, legalShiftAmts(idx)))
      expect(dshrOvers(idx), shr(inVal, ibp, outOfLegalShiftAmts(idx)))
      expect(dshs(idx), if (sel == 1) shl(inVal, legalShiftAmts(idx)) else shr(inVal, ibp, legalShiftAmts(idx)))

      val inbp = tDut.ins(idx).binaryPoint.get
      expect(bpsetMores(idx), bpset(inVal, inbp + bpDelta, inbp))
      expect(bpsetLesses(idx), bpset(inVal, Seq(inbp - bpDelta, 0).max, inbp))
      expect(bpset0s(idx), bpset(inVal, 0, inbp))
      expect(bpshls(idx), inVal)
      expect(bpshrToZeros(idx), bpshr(inVal, inbp, inbp))
      expect(bpshrs(idx), bpshr(inVal, inbp, Seq(bpDelta, inbp).min))
      expect(bpSet0Shls(idx), if (sel == 1) bpset(inVal, 0, inbp) else inVal)
      expect(
        bpSetMoreShrs(idx),
        if (sel == 1) bpset(inVal, inbp + bpDelta, inbp) else bpshr(inVal, inbp, Seq(bpDelta, inbp).min)
      )
    }
  }
}