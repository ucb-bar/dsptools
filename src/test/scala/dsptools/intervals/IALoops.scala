package dsptools.intervals.tests

import chisel3._
import chisel3.experimental._
import generatortools.io.CustomBundle
import generatortools.testing.TestModule
import org.scalatest.{Matchers, FlatSpec}
import chisel3.internal.firrtl.IntervalRange
import dsptools.DspTester
import dsptools.numbers._

import scala.collection.mutable.ArrayBuffer

class IALoops(r: IATestParams) extends Module {
  val Seq(aRange, bRange, cRange, dRange, _) = r.ranges
  val Seq(_, _, bp, _, _) = r.bps
  val init = 0.5
  // Warning: Not parameterized
  val loops = 4

  val inputNames = Seq("a", "b", "c", "d")

  val io = IO(new Bundle {
    val a = Input(Interval(aRange))
    val b = Input(Interval(bRange))
    val c = Input(Interval(cRange))
    val d = Input(Interval(dRange))
    val reg = IATest.outputs(inputNames, bp)
    val reg2 = IATest.outputs(inputNames, bp)
    val lit = Output(Interval(range"[?, ?].5"))

    val interval = Input(Interval(aRange))
    val intervalAsReal = Output(DspReal())
    val real = Input(DspReal())
    val realAsInterval = Output(Interval(aRange))
  })

  io.intervalAsReal := io.interval.asReal
  io.realAsInterval := io.real.toInterval(io.realAsInterval)

  val lit = Interval.fromDouble(value = 3.25, width = 16, binaryPoint = 5)
  io.lit := lit

  val ins = Seq(io.a, io.b, io.c, io.d)

  ins.zipWithIndex foreach { case (i, idx) =>
    // Accumulate 4x
    val range1 = loops.I * i + init.I(bp.BP)
    val reg = RegInit(Interval(), init.I(bp.BP))
    reg := (reg + i).reassignInterval(range1)

    // reg time sequence:   0.5, 0.5 + i, 0.5 + 2i, 0.5 + 3i, 0.5 + 4i
    // reg2 time sequence:  0  , 0.5    , 1 + i   , 1.5 + 3i, 2 + 6i     = range1 + 1.5 + 2i

    val range2 = range1 + (3 * init).I(bp.BP) + 2.I * i
    val reg2 = RegInit(Interval(), 0.I)
    reg2 := (reg2 + reg).reassignInterval(range2)

    io.reg.seq(idx) := reg
    io.reg2.seq(idx) := reg2
  }
}

class IALoopsSpec extends FlatSpec with Matchers {

  behavior of "IA Loop"

  it should "properly infer [_, _] ranges and compute" in {
    dsptools.Driver.execute(() => new TestModule(() => new IALoops(IATest.cc)), IATest.options("cc", backend = "verilator")) {
      c => new IALoopsTester(c)
    } should be (true)
  }

  it should "properly infer [_, _) ranges and compute" in {
    dsptools.Driver.execute(() => new TestModule(() => new IALoops(IATest.co)), IATest.options("co", backend = "verilator")) {
      c => new IALoopsTester(c)
    } should be (true)
  }

  it should "properly infer (_, _] ranges and compute" in {
    dsptools.Driver.execute(() => new TestModule(() => new IALoops(IATest.oc)), IATest.options("oc", backend = "verilator")) {
      c => new IALoopsTester(c)
    } should be (true)
  }

  it should "properly infer (_, _) ranges and compute" in {
    dsptools.Driver.execute(() => new TestModule(() => new IALoops(IATest.oo)), IATest.options("oo", backend = "verilator")) {
      c => new IALoopsTester(c)
    } should be (true)
  }

}

class IALoopsTester(testMod: TestModule[IALoops]) extends DspTester(testMod) {
  val tDut = testMod.dut
  val inputNames = tDut.inputNames

  val Seq(as, bs, cs, ds) = inputNames map {str =>
    val possibleVals = testMod.getDutIO(str).asInstanceOf[Interval].range.getPossibleValues
    println(s"$str min: ${possibleVals.min}, $str max: ${possibleVals.max}")
    Seq(possibleVals.min, possibleVals.max)
  }

  def getO(name: String) = {
    testMod.getIO(name).asInstanceOf[CustomBundle[Data]].seq
  }

  val idxs = 0 to 1
  var reg = ArrayBuffer(0.0, 0.0, 0.0, 0.0)
  var reg2 = ArrayBuffer(0.0, 0.0, 0.0, 0.0)
  for (idx <- idxs) {

    val inVals = Seq(as(idx), bs(idx), cs(idx), ds(idx))

    reset(1)
    for (loop <- 0 until tDut.loops) {
      inputNames.zip(inVals) foreach { case (name, value) =>
        poke(testMod.getIO(name), value)
        if (name == "a") {
          poke(testMod.getIO("interval"), value)
          poke(testMod.getIO("real"), value)
          updatableDspVerbose.withValue(true) {
            expect(testMod.getIO("intervalAsReal"), value)
            expect(testMod.getIO("realAsInterval"), value)
          }
        }
      }

      tDut.ins.zipWithIndex foreach { case (i, idx) =>
        if (loop == 0) {
          reg.update(idx, tDut.init)
          reg2.update(idx, 0.0)
        }
        else {
          reg2.update(idx, reg2(idx) + reg(idx))
          reg.update(idx, reg(idx) + inVals(idx))
        }
        expect(getO("reg")(idx), reg(idx))
        expect(getO("reg2")(idx), reg2(idx))

      }

      step(1)
    }

  }
}