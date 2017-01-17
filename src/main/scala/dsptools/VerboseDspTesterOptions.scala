package dsptools

import firrtl.{ComposableOptions, ExecutionOptionsManager}
import chisel3.iotesters.TesterOptionsManager

case class VerboseDspTesterOptions(
    isVerbose: Boolean = true,
    fixTolLSBs: Int = 1,
    realTolDecPts: Int = 8,
    genVerilogTb: Boolean = false,
    clkMul: Int = 1,
    tbTimeUnitPs: Int = 100,
    tbTimePrecisionPs: Int = 10) extends ComposableOptions {

    val clkPeriodPs = tbTimeUnitPs * clkMul

    require(tbTimeUnitPs >= tbTimePrecisionPs, "Time unit should be >= precision")
    require(clkPeriodPs / 2 > tbTimePrecisionPs, "Half a clk period should be greater than time precision")
    require(clkPeriodPs % 2 == 0, "Clk period should be divisible by 2")

}

trait HasVerboseDspTesterOptions {

  self: ExecutionOptionsManager =>

  var verboseDspTesterOptions = VerboseDspTesterOptions()

  parser.note("verbose dsp tester options")

  parser.opt[Unit]("verbose-tester-is-verbose")
    .abbr("vtiv")
    .foreach { _ => verboseDspTesterOptions = verboseDspTesterOptions.copy(isVerbose = true) }
    .text(s"set verbose flag on VerboseDspTesters, default is ${verboseDspTesterOptions.isVerbose}")

  parser.opt[Unit]("gen-verilog-tb")
    .abbr("gvtb")
    .foreach { _ => verboseDspTesterOptions = verboseDspTesterOptions.copy(genVerilogTb = true) }
    .text(s"set flag to generate tb .v file mimicking peek/poke, default is ${verboseDspTesterOptions.genVerilogTb}")

  parser.opt[Int]("fix-tol-lsb")
    .abbr("ftlsb")
    .foreach { x => verboseDspTesterOptions = verboseDspTesterOptions.copy(fixTolLSBs = x) }
    .text(s"fixed pt. expect tolerance (# wrong LSBs OK), default is ${verboseDspTesterOptions.fixTolLSBs}")

  parser.opt[Int]("real-tol-dec-pts")
    .abbr("rtdec")
    .foreach { x => verboseDspTesterOptions = verboseDspTesterOptions.copy(realTolDecPts = x) }
    .text(s"real expect error tolerance (1e-n), default is ${verboseDspTesterOptions.realTolDecPts}")

  parser.opt[Int]("tb-time-unit-ps")
    .abbr("tbunitps")
    .foreach { x => verboseDspTesterOptions = verboseDspTesterOptions.copy(tbTimeUnitPs = x) }
    .text(s"tb time unit (# x) in ps, default is ${verboseDspTesterOptions.tbTimeUnitPs}")

  parser.opt[Int]("tb-time-prec-ps")
    .abbr("tbprecps")
    .foreach { x => verboseDspTesterOptions = verboseDspTesterOptions.copy(tbTimePrecisionPs = x) }
    .text(s"tb time precision in ps, default is ${verboseDspTesterOptions.tbTimePrecisionPs}")

  parser.opt[Int]("clk-mul")
    .abbr("clkm")
    .foreach { x => verboseDspTesterOptions = verboseDspTesterOptions.copy(clkMul = x) }
    .text(s"clk period = clk-mul * time unit (ps), default is ${verboseDspTesterOptions.clkMul}")

}

class VerboseDspTesterOptionsManager
  extends TesterOptionsManager
    with HasVerboseDspTesterOptions {
}
