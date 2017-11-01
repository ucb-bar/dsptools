package dsptools.intervals.tests

import chisel3._
import chisel3.experimental._
import generatortools.io.CustomBundle
import generatortools.testing.TestModule
import org.scalatest.{Matchers, FlatSpec}
import chisel3.internal.firrtl.IntervalRange
import dsptools.DspTester

import scala.io.Source

@chiselName
class IAArith(r: IATestParams) extends Module {
  val Seq(aRange, bRange, cRange, dRange, selRange) = r.ranges
  val Seq(zbp, bp, sbp, _, lbp) = r.bps
  val Seq(c1, c2, c3, c4, c5, c6, c7, c8, c9) = r.consts

  val inputNames = Seq("a", "b", "c", "d")

  val io = IO(new Bundle {
    val a = Input(Interval(aRange))
    val b = Input(Interval(bRange))
    val c = Input(Interval(cRange))
    val d = Input(Interval(dRange))
    val sel = Input(Interval(selRange))

    val m1 = Output(Interval(range"[?, ?].$bp"))
    val m2 = Output(Interval(range"[?, ?].$bp"))
    val m3 = Output(Interval(range"[?, ?].$bp"))
    val m4 = Output(Interval(range"[?, ?].$bp"))
    val m5 = Output(Interval(range"[?, ?].$bp"))
    val m6 = Output(Interval(range"[?, ?].$bp"))
  })

  val const1 = c1.I(sbp.BP)
  val const2 = c2.I(zbp.BP)
  val const3 = c3.I(zbp.BP)
  val const1Long = c1.I(lbp.BP)

  val ins = Seq(io.a, io.b, io.c, io.d)

  val sel = RegNext(io.sel)

  val t1 = io.a * io.b
  val t2 = io.c * io.d

  val t3 = t1 - io.a
  val t4 = t2 + io.b

  val t5 = t3 + (-io.c)
  val t6 = t4 + io.d

  val t7 = RegNext(t5 + const1)
  val t8 = RegNext(t6 - const2)

  val t9 = (t7 + t8) * const3

  val m1 = Mux(sel < c7.I(bp.BP), t9, const1Long)
  val m2 = Mux(sel <= c8.I(zbp.BP), t9, const2)
  val m3 = Mux(sel === c9.I(zbp.BP), t9, const3)
  val m4 = Mux(sel >= c9.I(zbp.BP), t9, c4.I(lbp.BP))
  val m5 = Mux(sel > c9.I(zbp.BP), t9, c5.I(lbp.BP))
  val m6 = Mux(sel =/= c9.I(zbp.BP), t9, c6.I(zbp.BP))

  io.m1 := m1
  io.m2 := m2
  io.m3 := m3
  io.m4 := m4
  io.m5 := m5
  io.m6 := m6

}

class IAArithSpec extends FlatSpec with Matchers {

  behavior of "IA for +, -, *, as*, <, <=, ===, >=, >, =/=, Mux, Reg"

  it should "properly infer [_, _] ranges and compute" in {
    dsptools.Driver.execute(() => new TestModule(() => new IAArith(IATest.cc)), IATest.options("cc")) {
      c => new IAArithTester(c)
    } should be (true)
  }

  it should "properly infer [_, _] ranges and compute, and bit reduce" in {
    dsptools.Driver.executeWithBitReduction(() => new TestModule(() => new IAArith(IATest.cc)), IATest.options("cc")) {
      c => new IAArithTester(c)
    } should be (true)
  }

  it should "properly infer [_, _) ranges and compute" in {
    dsptools.Driver.execute(() => new TestModule(() => new IAArith(IATest.co)), IATest.options("co")) {
      c => new IAArithTester(c)
    } should be (true)
  }

  it should "properly infer (_, _] ranges and compute" in {
    dsptools.Driver.execute(() => new TestModule(() => new IAArith(IATest.oc)), IATest.options("oc")) {
      c => new IAArithTester(c)
    } should be (true)
  }

  it should "properly infer (_, _) ranges and compute" in {

    var resultsDir = ""
    dsptools.Driver.execute(() => new TestModule(() => new IAArith(IATest.oo)), IATest.options("oo")) {
      c =>
        val out = new IAArithTester(c)
        val iotestersOM = chisel3.iotesters.Driver.optionsManager
        val targetDir = iotestersOM.targetDirName
        resultsDir = targetDir
        out
    } should be (true)

    // Get test vectors from resources folder
    val stream = getClass.getResourceAsStream("/dsptools/intervals/tests/instrumentation.csv")
    val expected = scala.io.Source.fromInputStream(stream).getLines
    val lines = Source.fromFile(resultsDir + "/TestModule.signals.csv").getLines.toList

    for (e <- expected) {
      val eCols = e.split(",").map(_.trim)
      val eNameTpe = eCols.head
      val eMin = eCols(2)
      val eMax = eCols(3)
      val expectedParams = s"$eNameTpe $eMin $eMax"
      val aCols = lines.find(_.startsWith(eNameTpe)).get.split(",").map(_.trim)
      val aNameTpe = aCols.head
      val aMin = aCols(2)
      val aMax = aCols(3)
      val actualParams = s"$aNameTpe $aMin $aMax"
      require(
        expectedParams == actualParams,
        s"Instrumentation failed :(. Expected: $expectedParams. Actual $actualParams"
      )
    }
  }

}

class IAArithTester(testMod: TestModule[IAArith]) extends DspTester(testMod) {
  val tDut = testMod.dut
  val inputNames = tDut.inputNames :+ "sel"

  val Seq(as, bs, cs, ds, sels) = inputNames map {str =>
    val possibleVals = testMod.getDutIO(str).asInstanceOf[Interval].range.getPossibleValues
    println(s"$str min: ${possibleVals.min}, $str max: ${possibleVals.max}")
    possibleVals
  }

  def getO(name: String) = {
    testMod.getIO(name).asInstanceOf[CustomBundle[Data]].seq
  }

  for (a <- as; b <- bs; c <- cs; d <- ds; sel <- sels) {
    val t1 = a * b
    val t2 = c * d
    val t3 = t1 - a
    val t4 = t2 + b
    val t5 = t3 - c
    val t6 = t4 + d
    val t7 = t5 + tDut.c1
    val t8 = t6 - tDut.c2
    val t9 = (t7 + t8) * tDut.c3

    val inVals = Seq(a, b, c, d, sel)

    inputNames.zip(inVals) foreach { case (name, value) =>
      poke(testMod.getIO(name), value)
    }

    step(1)

    expect(testMod.getIO("m1"), if (sel < tDut.c7) t9 else tDut.c1)
    expect(testMod.getIO("m2"), if (sel <= tDut.c8) t9 else tDut.c2)
    expect(testMod.getIO("m3"), if (sel == tDut.c9) t9 else tDut.c3)
    expect(testMod.getIO("m4"), if (sel >= tDut.c9) t9 else tDut.c4)
    expect(testMod.getIO("m5"), if (sel > tDut.c9) t9 else tDut.c5)
    expect(testMod.getIO("m6"), if (sel != tDut.c9) t9 else tDut.c6)

  }

}

/*
OPEN, OPEN

a = (-2.96875, 2.28125).5 --> (-95, 73), WIDTH = 8
b = (5, 8).0 --> (5, 8), WIDTH = 4 + 1
c = (-6, -4).0 --> (-6, -4), WIDTH = 4
d = (-4, 3).0 --> (-4, 3), WIDTH = 3
sel = (-2, 4).0 --> (-2, 4), WIDTH = 4

const1 == (-18.3).12 --> -74,956.8 --> -18.300048828 (-74,957),  WIDTH = 18
const2 = (-4).0, WIDTH = 3
const3 = (8).0, WIDTH = 4 + 1
const1Long = (-18.3).15 --> -599654.4 --> -18.299987793 (-599654), WIDTH = 21

c4 = (-20).15
c5 = (10).15
c6 = (5).0

t1 = a * b = (-23.75, 18.25).5 --> (-760, 584), WIDTH = 11
t2 = c * d = (-18, 24).0 --> (-18, 24), WIDTH = 6
t3 = t1 - a = (-26.03125, 21.21875).5 --> (-833, 679), WIDTH = 11
   = (a * b) - a = a * (b - 1) = (-2.96875, 2.28125).5 * (4, 7).0 --> (-20.78125, 15.96875).5 --> (-665, 511), WIDTH = 11 NARROWER BOUND
t4 = t2 + b = (-13, 32).0 --> (-13, 32), WIDTH = 7
t5 = t3 - c = (-22.03125, 27.21875).5 --> (-705, 871), WIDTH = 11
   = (-20.78125, 15.96875).5 - (-6, -4).0 = (-16.78125, 21.96875).5 --> (-537, 703), WIDTH = 11 NARROWER BOUND
t6 = t4 + d = (-17, 35).0 --> (-17, 35) WIDTH = 7
   = (c * d) + b + d = d * (c + 1) + b = (-4, 3) * (-5, -3) + (5, 8) = (-15, 20) + (5, 8) = (-10, 28), WIDTH = 6
t7 = RegNext(t5 + const1) = (-40.331298828, 8.918701172).12 --> (-165197, 36531), WIDTH = 19
   = (-16.78125, 21.96875).5 - 18.300048828 = (-35.081298828, 3.668701172).12 --> (-143693, 15072), WIDTH = 19
t8 = RegNext(t6 - const2) = (-13, 39).0 --> (-13, 39), WIDTH = 7
   = (-10, 28) + 4 --> (-6, 32), WIDTH = 7
t7 + t8 = (-40.331298828, 8.918701172).12 + (-13, 39).0 = (-53.331298828, 47.918701172).12 --> (-218445, 196275), WIDTH = 19
        = (t5 + const1) + (t6 - const2) = t3 - c + const1 + t4 + d - const2 = t1 - a - c + const1 + t2 + b + d - const2 = a * b - a - c + const1 + c * d + b + d - const2 <-- TOO HARD TO HAND OPT
t9 = (t7 + t8) * const3 = (-426.650390624, 383.349609376).12 --> (-1747560, 1570200), WIDTH = 22
   TOO HARD TO HAND OPT

t9 shifted left 3 times (to match bp): (-13980480, 12561600), WIDTH = 25

m1 = Mux _, t9, const1Long: (-13980480, 12561600), WIDTH = 25 [BP = 15] --> 15 @ Output
m2 = Mux _, t9, const2: (-1747560, 1570200), WIDTH = 22 [BP = 12] --> 15 @ Output
m3 = Mux _, t9, const3: (-1747560, 1570200), WIDTH = 22 [BP = 12]
m4 = Mux _, t9, c4: (-13980480, 12561600), WIDTH = 25 [BP = 15]
m5 = Mux _, t9, c5: (-13980480, 12561600), WIDTH = 25 [BP = 15]
m6 = Mux _, t9, c6: (-1747560, 1570200), WIDTH = 22 [BP = 12]
*/