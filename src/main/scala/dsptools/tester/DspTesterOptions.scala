// SPDX-License-Identifier: Apache-2.0

package dsptools

import firrtl.{ComposableOptions, ExecutionOptionsManager}
import chisel3.iotesters.TesterOptionsManager

case class DspTesterOptions(
    // Top-level TB verbosity
    isVerbose: Boolean = true,
    // Expect Tolerance in LSBs for FixedPoint, SInt, UInt
    fixTolLSBs: Int = 0,
    // 10^(-realTolDecPts) tolerance for expect on DspReal
    realTolDecPts: Int = 8,
    // Generated mirroed Verilog TB from peek/poke
    genVerilogTb: Boolean = false,
    // clk period in ps = clkMul * tbTimeUnitPs
    clkMul: Int = 1,
    // Time unit in ps
    tbTimeUnitPs: Int = 100,
    // Time precision in ps
    tbTimePrecisionPs: Int = 10,
    // Input/output delay after which to peek/poke values (some fraction of clkMul)
    inOutDelay: Double = 0.5,
    // # clk periods for initial reset
    initClkPeriods: Int = 5,
    // # bit reduce to within this many sigma of mean, only applicable when using executeWithBitReduction
    bitReduceBySigma: Double = 0.0,
    // # max number of bit reduction passes
    bitReduceMaxPasses: Int = 1,
    // bitReduce, add this many bits to reduce amount,
    bitReduceFudgeConstant: Int = 0
) extends ComposableOptions {

    val clkPeriodPs = tbTimeUnitPs * clkMul
    val initTimeUnits = clkMul * initClkPeriods

    require(tbTimeUnitPs >= tbTimePrecisionPs, "Time unit should be >= precision")
    require(clkPeriodPs / 2 > tbTimePrecisionPs, "Half a clk period should be greater than time precision")
    require(clkPeriodPs % 2 == 0, "Clk period should be divisible by 2")
    require(initClkPeriods >= 1, "Reset should be applied for at least 1 clk period")

}

trait HasDspTesterOptions {

  self: ExecutionOptionsManager =>

  var dspTesterOptions = DspTesterOptions()

  parser.note("dsp tester options")

  parser.opt[Unit]("dsp-tester-is-verbose")
    .abbr("dtiv")
    .foreach { _ => dspTesterOptions = dspTesterOptions.copy(isVerbose = true) }
    .text(s"set verbose flag on DspTesters, default is ${dspTesterOptions.isVerbose}")

  parser.opt[Unit]("dsp-tester-is-not-verbose")
    .abbr("dtinv")
    .foreach { _ => dspTesterOptions = dspTesterOptions.copy(isVerbose = false) }
    .text(s"unset verbose flag on DspTesters, default is ${dspTesterOptions.isVerbose}")

  parser.opt[Unit]("gen-verilog-tb")
    .abbr("gvtb")
    .foreach { _ => dspTesterOptions = dspTesterOptions.copy(genVerilogTb = true) }
    .text(s"set flag to generate tb .v file mimicking peek/poke, default is ${dspTesterOptions.genVerilogTb}")

  parser.opt[Int]("fix-tol-lsb")
    .abbr("ftlsb")
    .foreach { x => dspTesterOptions = dspTesterOptions.copy(fixTolLSBs = x) }
    .text(s"fixed pt, sint, uint expect tolerance (# wrong LSBs OK), default is ${dspTesterOptions.fixTolLSBs}")

  parser.opt[Int]("real-tol-dec-pts")
    .abbr("rtdec")
    .foreach { x => dspTesterOptions = dspTesterOptions.copy(realTolDecPts = x) }
    .text(s"real expect error tolerance (1e-n), default is ${dspTesterOptions.realTolDecPts}")

  parser.opt[Int]("tb-time-unit-ps")
    .abbr("tbunitps")
    .foreach { x => dspTesterOptions = dspTesterOptions.copy(tbTimeUnitPs = x) }
    .text(s"tb time unit (# x) in ps, default is ${dspTesterOptions.tbTimeUnitPs}")

  parser.opt[Int]("tb-time-prec-ps")
    .abbr("tbprecps")
    .foreach { x => dspTesterOptions = dspTesterOptions.copy(tbTimePrecisionPs = x) }
    .text(s"tb time precision in ps, default is ${dspTesterOptions.tbTimePrecisionPs}")

  parser.opt[Int]("clk-mul")
    .abbr("clkm")
    .foreach { x => dspTesterOptions = dspTesterOptions.copy(clkMul = x) }
    .text(s"clk period = clk-mul * time unit (ps), default is ${dspTesterOptions.clkMul}")

  parser.opt[Int]("init-clk-periods")
    .abbr("initt")
    .foreach { x => dspTesterOptions = dspTesterOptions.copy(initClkPeriods = x) }
    .text(s"initial reset time (# of clk periods), default is ${dspTesterOptions.initClkPeriods}")

  parser.opt[Double]("bit-reduce-to-n-sigma")
    .abbr("brtns")
    .foreach { x => dspTesterOptions = dspTesterOptions.copy(bitReduceBySigma = x) }
    .text(
      s"bit reduce to within mean + (this * sigma), default is ${dspTesterOptions.bitReduceBySigma} which => don't use"
    )

  parser.opt[Int]("bit-reduce-max-passes")
    .abbr("brmp")
    .foreach { x => dspTesterOptions = dspTesterOptions.copy(bitReduceMaxPasses = x) }
    .text(
      s"bit reduce repeatedly until no changes or this many passes)," +
        s"default is ${dspTesterOptions.bitReduceMaxPasses}"
    )

  parser.opt[Int]("bit-reduce-fudge-constant")
    .abbr("brgb")
    .foreach { x => dspTesterOptions = dspTesterOptions.copy(bitReduceFudgeConstant = x) }
    .text(
      s"bit-reduce guard bits, limit the reduction amount by adding this," +
        s"default is ${dspTesterOptions.bitReduceMaxPasses}"
    )

}

class DspTesterOptionsManager
  extends TesterOptionsManager
    with HasDspTesterOptions {
}
