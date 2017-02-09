package dsptools

import firrtl.{ComposableOptions, ExecutionOptionsManager}
import chisel3.iotesters.TesterOptionsManager

case class DspTesterOptions(
    isVerbose: Boolean = true,
    fixTolLSBs: Int = 1,
    realTolDecPts: Int = 8,
    genVerilogTb: Boolean = false,
    clkMul: Int = 1,
    tbTimeUnitPs: Int = 100,
    // Note: tb checking occurs 1 precision step after clk
    tbTimePrecisionPs: Int = 10,
    initClkPeriods: Int = 5) extends ComposableOptions {

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

  parser.note("verbose dsp tester options")

  parser.opt[Unit]("verbose-tester-is-verbose")
    .abbr("vtiv")
    .foreach { _ => dspTesterOptions = dspTesterOptions.copy(isVerbose = true) }
    .text(s"set verbose flag on DspTesters, default is ${dspTesterOptions.isVerbose}")

  parser.opt[Unit]("gen-verilog-tb")
    .abbr("gvtb")
    .foreach { _ => dspTesterOptions = dspTesterOptions.copy(genVerilogTb = true) }
    .text(s"set flag to generate tb .v file mimicking peek/poke, default is ${dspTesterOptions.genVerilogTb}")

  parser.opt[Int]("fix-tol-lsb")
    .abbr("ftlsb")
    .foreach { x => dspTesterOptions = dspTesterOptions.copy(fixTolLSBs = x) }
    .text(s"fixed pt. expect tolerance (# wrong LSBs OK), default is ${dspTesterOptions.fixTolLSBs}")

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

}

class DspTesterOptionsManager
  extends TesterOptionsManager
    with HasDspTesterOptions {
}
