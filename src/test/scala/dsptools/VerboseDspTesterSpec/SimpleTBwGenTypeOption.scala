// SPDX-License-Identifier: Apache-2.0

package SimpleTB

import chisel3._
import chisel3.util._
import chisel3.internal.firrtl.{Width, BinaryPoint}
import chisel3.experimental.FixedPoint
import breeze.math.Complex
import dsptools.{DspTester, DspTesterOptionsManager, DspTesterOptions}
import dsptools.numbers._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import chisel3.iotesters.TesterOptions

trait EasyPeekPoke {

  self: DspTester[_] =>

  def feed(d: Data, value: Double) = {
    d match {
      case b: Bool => poke(b, if (value == 0.0) false else true)
      case u: UInt => poke(u, math.abs(value.round).toInt)
      case r => poke(r, value)
    }
  }
  def feed(d: DspComplex[_], value: Complex) = poke(d, value)

  // TB Debug only [otherwise, just use expect for any *real* tb!]
  def checkP(d: Data, value: Double) {
    peek(d)
    // SInts are perfect in this case b/c they're just passed through (no loss of precision)
    d match {
      case _: SInt | _: UInt | _: Bool => fixTolLSBs.withValue(0) { check(d, value) }
      case _ => check(d, value)
    }
  }

  def checkP(d: DspComplex[_], value: Complex) = {
    peek(d)
    d.real match {
      case _: SInt | _: UInt | _: Bool => fixTolLSBs.withValue(0) {
        check(d, value)
      }
      case _ => check(d, value)
    }
  }

  // TODO: Add runtime tolerance change here as a shortcut?
  def check(d: Data, value: Double) {
    d match {
      case b: Bool => expect(b, if (value == 0.0) false else true)
      case u: UInt => expect(u, math.abs(value.round).toInt)
      case f: FixedPoint => {
        expect(f, value)
        require(f.binaryPoint.get > 0, "Fixed points used in this test have >0 fractional bits!")
      }
      case r => expect(r, value)
    }
  }
  def check(d: DspComplex[_], value: Complex) = expect(d, value)
}

case class TestParams(
    val smallW: Int = 8,
    val bigW: Int = 16,
    val smallBP: Int = 4,
    val bigBP: Int = 8,
    val posLit: Double = 3.3,
    val negLit: Double = -3.3,
    val lutVals: Seq[Double] = Seq(-3.3, -2.2, -1.1, -0.55, -0.4, 0.4, 0.55, 1.1, 2.2, 3.3)) {

  val genLongF = FixedPoint(bigW.W, bigBP.BP)
  val genShortF = FixedPoint(smallW.W, smallBP.BP)
  val genLongS = SInt(bigW.W)
  val genShortS = SInt(smallW.W)
  val genR = DspReal()
  val vecLen = lutVals.length

}

class DataTypeBundle[R <: Data:Real](genType: R, dataWidth: Width, binaryPoint: BinaryPoint) extends Bundle {
  val gen = genType.cloneType
  val s = SInt(dataWidth)
  val f = FixedPoint(dataWidth, binaryPoint)
  val u = UInt(dataWidth)
}

class Interface[R <: Data:Real](genShort: R, genLong: R, includeR: Boolean, p: TestParams) extends Bundle {

  val smallW = p.smallW.W
  val bigW = p.bigW.W
  val smallBP = p.smallBP.BP
  val bigBP = p.bigBP.BP
  val vecLen = p.vecLen

  val r = if (includeR) Some(DspReal()) else None
  val RP = if (includeR) Some(DspReal()) else None
  val RN = if (includeR) Some(DspReal()) else None
  val b = Bool()
  val cGenL = DspComplex(genLong)
  val cFS = DspComplex(FixedPoint(smallW, smallBP))
  val cR = if (includeR) Some(DspComplex(DspReal())) else None

  val short = new DataTypeBundle(genShort, smallW, smallBP)
  val long = new DataTypeBundle(genLong, bigW, bigBP)

  val vU = Vec(vecLen, UInt(smallW))
  val vS = Vec(vecLen, SInt(smallW))
  val vF = Vec(vecLen, FixedPoint(smallW, smallBP))
}

class SimpleIOModule[R <: Data:Real](genShort: R, genLong: R, val includeR: Boolean, val p: TestParams)
    extends Module {

  val io = IO(new Bundle {
    val i = Input(new Interface(genShort, genLong, includeR, p))
    val o = Output(new Interface(genShort, genLong, includeR, p)) })

  // Crossed sizes on purpose (to make sure Fixed point was interpreted correctly)
  io.o.long <> RegNext(io.i.short)
  io.o.short <> RegNext(io.i.long)

  if (includeR) io.o.r.get := RegNext(io.i.r.get)
  io.o.b := RegNext(io.i.b)
  io.o.cGenL := RegNext(io.i.cGenL)
  io.o.cFS := RegNext(io.i.cFS)
  if (includeR) io.o.cR.get := RegNext(io.i.cR.get)

  io.o.vU := RegNext(io.i.vU)
  io.o.vS := RegNext(io.i.vS)
  io.o.vF := RegNext(io.i.vF)

  if (includeR) {
    io.o.RP.get := DspReal(0)
    io.o.RN.get := DspReal(0)
  }
}

class SimpleLitModule[R <: Data:Real](genShort: R, genLong: R, val includeR: Boolean, val p: TestParams)
    extends Module {

  val posLit = p.posLit
  val negLit = p.negLit
  val lutVals = p.lutVals
  val bp = p.smallBP

  val io = IO(new Bundle {
    val i = Input(new Interface(genShort, genLong, includeR, p))
    val o = Output(new Interface(genShort, genLong, includeR, p)) })

  val litRP = if (includeR) Some(DspReal(posLit)) else None
  val litRN = if (includeR) Some(DspReal(negLit)) else None

  val pos = new Bundle {
    val litG = genShort.fromDouble(posLit)
    val litS = posLit.round.toInt.S
    val litF = posLit.F(bp.BP)
  }

  val neg = new Bundle {
    val litG = genShort.fromDouble(negLit)
    val litS = negLit.round.toInt.S
    val litF = negLit.F(bp.BP)
  }

  val litB = true.B
  val litU = posLit.round.toInt.U
  val litC = DspComplex(posLit.F(bp.BP), negLit.F(bp.BP))

  val lutGenSeq = lutVals map {x => genShort.fromDoubleWithFixedWidth(x)}
  val lutSSeq = lutVals map (_.round.toInt.S(p.smallW.W))

  val lutGen = VecInit(lutGenSeq)
  val lutS = VecInit(lutSSeq)

  io.o.vU := RegNext(io.i.vU)
  io.o.vS := RegNext(io.i.vS)
  io.o.vF := RegNext(io.i.vF)
  io.o.long := io.i.long
  io.o.short := io.i.short
  io.o.b := io.i.b
  io.o.cGenL := io.i.cGenL
  io.o.cFS := io.i.cFS
  io.o.short.gen := lutGen(io.i.short.u)
  io.o.short.s := lutS(io.i.short.u)
  if (includeR) {
    io.o.RP.get := litRP.get
    io.o.RN.get := litRN.get
    io.o.r.get := io.i.r.get
    io.o.cR.get := io.i.cR.get
  }
}

class PassIOTester[R <: Data:Real](c: SimpleIOModule[R]) extends DspTester(c) with EasyPeekPoke {

  val lutVals = c.p.lutVals
  val io = c.io

  // SInts auto rounded; UInts auto absolute valued + rounded
  (lutVals :+ lutVals.head).zipWithIndex foreach { case (value, i) => {
    (io.i.short.elements.unzip._2 ++ io.i.long.elements.unzip._2) foreach  { a => feed(a, value) }
    val complexVal = Complex(value, -value)
    if (c.includeR) feed(io.i.r.get, value)
    feed(io.i.b, value)
    feed(io.i.cGenL, complexVal)
    feed(io.i.cFS, complexVal)
    if (c.includeR) feed(io.i.cR.get, complexVal)
    if (i != 0) {
      val prevVal = lutVals(i-1)
      val prevComplexVal = Complex(prevVal, -prevVal)
      io.o.short.elements.unzip._2 foreach { a => checkP(a, prevVal) }
      if (c.includeR) checkP(io.o.r.get, prevVal)
      checkP(io.o.b, prevVal)
      checkP(io.o.cGenL, prevComplexVal)
      checkP(io.o.cFS, prevComplexVal)
      if (c.includeR) checkP(io.o.cR.get, prevComplexVal)
      // Since long outputs come from short inputs, the tolerance must match that of smallBP
      fixTolLSBs.withValue(c.p.bigBP - c.p.smallBP + fixTolLSBs.value) {
        io.o.long.elements.unzip._2 foreach { a => checkP(a, prevVal) }
      }
    }
    step(1)
  }}

  lutVals.zipWithIndex foreach { case (value, i) => {
    feed(io.i.vU(i), value)
    feed(io.i.vS(i), value)
    feed(io.i.vF(i), value)
  }}

  step(5)
  lutVals.reverse.zipWithIndex foreach { case (value, i) => {
    feed(io.i.vU(i), value)
    feed(io.i.vS(i), value)
    feed(io.i.vF(i), value)
    checkP(io.o.vU(i), lutVals(i))
    checkP(io.o.vS(i), lutVals(i))
    checkP(io.o.vF(i), lutVals(i))
  }}

  step(5)
  lutVals.reverse.zipWithIndex foreach { case (value, i) => {
    feed(io.i.vU(i), 0)
    feed(io.i.vS(i), 0)
    feed(io.i.vF(i), 0)
    checkP(io.o.vU(i), value)
    checkP(io.o.vS(i), value)
    checkP(io.o.vF(i), value)
  }}

  peek(io.o.vU)
  peek(io.o.vS)

}

class PassLitTester[R <: Data:Real](c: SimpleLitModule[R]) extends DspTester(c) with EasyPeekPoke {

  val posLit = c.posLit
  val negLit = c.negLit
  val lutVals = c.lutVals

  if (c.includeR) {
    checkP(c.io.o.RP.get, posLit)
    checkP(c.io.o.RN.get, negLit)
  }

  // expect properly rounds doubles to ints for SInt
  c.pos.elements foreach { case (s, d) => checkP(d, posLit) }
  c.neg.elements foreach { case (s, d) => checkP(d, negLit) }

  checkP(c.litB, 1)
  checkP(c.litU, posLit)

  checkP(c.litC, Complex(posLit, negLit))

  // peek(c.lutS) doesn't work -- can't peek elements of Vec[Lit]
  // expect auto rounds SInts
  c.lutSSeq.zipWithIndex foreach { case (x, i) => checkP(x, lutVals(i)) }
  c.lutGenSeq.zipWithIndex foreach { case (x, i) => checkP(x, lutVals(i)) }

  c.lutVals.zipWithIndex foreach { case (x, i) => {
    poke(c.io.i.short.u, i)
    checkP(c.io.o.short.gen, x)

    // How to change expect tolerance (for Lits; SInts should match exactly)
    // expect automatically rounds when data is SInt (whereas expect doesn't)
    fixTolLSBs.withValue(0) {
      checkP(c.io.o.short.s, x)
    }

  }}

  peek(c.lutGenSeq(0))
  peek(c.lutSSeq(0))
}

class FailLitTester[R <: Data:Real](c: SimpleLitModule[R]) extends DspTester(c) {

  val posLit = c.posLit
  val negLit = c.negLit
  val lutVals = c.lutVals

  c.pos.elements foreach { case (s, d) => {
    d match {
      case f: FixedPoint => expect(f, posLit)
      case _ => } } }

  c.neg.elements foreach { case (s, d) => {
    d match {
      case f: FixedPoint => expect(f, negLit)
      case _ => } } }

  expect(c.litC, Complex(posLit, negLit))

  c.lutGenSeq.zipWithIndex foreach { case (x, i) => {
    expect(x, lutVals(i)) }}

  c.lutVals.zipWithIndex foreach { case (x, i) => {
    poke(c.io.i.short.u, i)
    expect(c.io.o.short.gen, x)
  }}

}

object TestSetup {
  val p = TestParams()

  val testerOptionsGlobal = TesterOptions(
      isVerbose = false,
      displayBase = 16,
      backendName = "verilator",
      isGenVerilog = true)

  val optionsPass = new DspTesterOptionsManager {
      dspTesterOptions = DspTesterOptions(
          fixTolLSBs = 1,
          genVerilogTb = false,
          isVerbose = false)
      testerOptions = testerOptionsGlobal
    }

  val optionsFail = new DspTesterOptionsManager {
    dspTesterOptions = optionsPass.dspTesterOptions.copy(fixTolLSBs = 0)
    testerOptions = testerOptionsGlobal
  }

  val optionsPassTB = new DspTesterOptionsManager {
    dspTesterOptions = optionsPass.dspTesterOptions.copy(genVerilogTb = true)
    testerOptions = testerOptionsGlobal
  }

}

class SimpleTBSpec extends AnyFlatSpec with Matchers {

  val p = TestSetup.p
  val optionsPass = TestSetup.optionsPass
  val optionsPassTB = TestSetup.optionsPassTB
  val optionsFail = TestSetup.optionsFail

  // Note: Verilator simulation is silly. Concurrent testing gets confused when things are in the same directory.

  behavior of "simple module lits"

  ignore should "properly read lits with gen = sint (reals rounded) and expect tolerance set to 1 bit" in {
    val opt = new DspTesterOptionsManager {
      dspTesterOptions = optionsPass.dspTesterOptions
      testerOptions = optionsPass.testerOptions
//      commonOptions = optionsPass.commonOptions.copy(targetDirName = "test_run_dir/lit_sint")
    }
    dsptools.Driver.execute(() => new SimpleLitModule(p.genShortS, p.genLongS, includeR = true, p), opt) { c =>
      new PassLitTester(c)
    } should be (true)
  }

  ignore should "properly read lits with gen = fixed and expect tolerance set to 1 bit " +
      "(even with finite fractional bits)" in {
    val opt = new DspTesterOptionsManager {
      dspTesterOptions = optionsPass.dspTesterOptions
      testerOptions = optionsPass.testerOptions
//      commonOptions = optionsPass.commonOptions.copy(targetDirName = "test_run_dir/lit_fix")
    }
    dsptools.Driver.execute(() => new SimpleLitModule(p.genShortF, p.genLongF, includeR = true, p), opt) { c =>
      new PassLitTester(c)
    } should be (true)
  }

  ignore should "*fail* to read all lits with gen = fixed when expect tolerance is set to 0 bits " +
      "(due to not having enough fractional bits to represent #s)" in {
    val opt = new DspTesterOptionsManager {
      dspTesterOptions = optionsFail.dspTesterOptions
      testerOptions = optionsFail.testerOptions
//      commonOptions = optionsFail.commonOptions.copy(targetDirName = "test_run_dir/lit_fix")
    }
    dsptools.Driver.execute(() => new SimpleLitModule(p.genShortF, p.genLongF, includeR = true, p), opt) { c =>
      new FailLitTester(c)
    } should be (false)
  }

  behavior of "simple module registered io"

  it should "properly poke/peek io delayed 1 cycle with gen = sint (reals rounded) " +
      "and expect tolerance set to 1 bit" in {
    val opt = new DspTesterOptionsManager {
      dspTesterOptions = optionsPass.dspTesterOptions
      testerOptions = optionsPass.testerOptions
//      commonOptions = optionsPass.commonOptions.copy(targetDirName = "test_run_dir/io_sint")
    }
    dsptools.Driver.execute(() => new SimpleIOModule(p.genShortS, p.genLongS, includeR = true, p), opt) { c =>
      new PassIOTester(c)
    } should be (true)
  }

  it should "properly poke/peek io delayed 1 cycle with gen = fixed and expect tolerance set to 1 bit" in {
    val opt = new DspTesterOptionsManager {
      dspTesterOptions = optionsPass.dspTesterOptions
      testerOptions = optionsPass.testerOptions
//      commonOptions = optionsPass.commonOptions.copy(targetDirName = "test_run_dir/io_fix")
    }
    dsptools.Driver.execute(() => new SimpleIOModule(p.genShortF, p.genLongF, includeR = true, p), opt) { c =>
      new PassIOTester(c)
    } should be (true)
  }

  it should "properly poke/peek io delayed 1 cycle with gen = fixed + print TB (no reals)" in {
    this.synchronized {
      val opt = new DspTesterOptionsManager {
        dspTesterOptions = optionsPassTB.dspTesterOptions
        testerOptions = optionsPassTB.testerOptions
        commonOptions = optionsPassTB.commonOptions.copy(targetDirName = "test_run_dir/io_fix_tb")
      }
      var tbFileLoc: String = ""
      dsptools.Driver.execute(() => new SimpleIOModule(p.genShortF, p.genLongF, includeR = false, p),
          opt) { c => {
        val tester = new PassIOTester(c)
        tbFileLoc = tester.tbFileName
        tester
      } } should be (true)
      println(s"The file is here: ${tbFileLoc}")
      val tbTxt = scala.io.Source.fromFile(tbFileLoc).getLines
      // This is a lot easier in Scala 2.12.x
      val resourceGoldenModel = getClass.getResourceAsStream("/TBGoldenModel.v")
      val TbGoldenModelTxt = scala.io.Source.fromInputStream(resourceGoldenModel).getLines
      TbGoldenModelTxt.zip(tbTxt) foreach { case (expected, in) =>
        expected should be (in)
      }
    }
  }

}
