package dsptools.intervals.tests

import chisel3._
import chisel3.iotesters.TesterOptions
import dsptools.{DspTesterOptionsManager, DspTesterOptions}
import chisel3.internal.firrtl.IntervalRange
import chisel3.experimental._
import firrtl.ir.Closed
import generatortools.io.CustomBundle
import logger.LogLevel
import firrtl_interpreter.InterpreterOptions

object IATest {

  def options(testName: String = "", verbose: Boolean = false, trace: Boolean = false) = new DspTesterOptionsManager {
    dspTesterOptions = DspTesterOptions(fixTolLSBs = 1, isVerbose = verbose)
    testerOptions = TesterOptions(isVerbose = false, displayBase = 2, backendName = "firrtl")
    commonOptions = 
      if(!trace) commonOptions.copy(targetDirName = "test_run_dir/IATests/" + testName)
      else commonOptions.copy(targetDirName = "test_run_dir/IATests/" + testName, globalLogLevel = LogLevel.Trace)
    // DEBUG note: globalLogLevel = LogLevel.Trace to print intermediate forms of FIRRTL
    interpreterOptions = InterpreterOptions(
      monitorReportFileName = "signals.csv",
      monitorBitUsage = true,
      monitorHistogramBins = 4,
      monitorTrackTempNodes = false,
      prettyPrintReport = true
    )
  }

  val cc = IATestParams(
    ranges = Seq(
      range"[-3.0, 2.0].5",
      range"[4, 9]",
      range"[-7, -3]",
      range"[-5, 4]",
      range"[-1.0, 3.0].1"
    ),
    bps = Seq(0, 5, 12, 10, 15),
    consts = Seq(-18.3, -4, 8, -20, 10, 5, 1.25, 1, 2)
  )

  val co = IATestParams(
    ranges = Seq(
      range"[-3.0, 2.0).5",
      range"[4, 9)",
      range"[-7, -3)",
      range"[-5, 4)",
      range"[0, 4]"
    ),
    bps = Seq(0, 5, 12, 10, 15),
    consts = Seq(-18.3, -4, 8, -20, 10, 5, 1.25, 1, 2)
  )

  val oc = IATestParams(
    ranges = Seq(
      range"(-3.0, 2.3].5",
      range"(4, 9]",
      range"(-7, -3]",
      range"(-5, 4]",
      range"[-1, 3]"
    ),
    bps = Seq(0, 5, 12, 10, 15),
    consts = Seq(-18.3, -4, 8, -20, 10, 5, 1.25, 1, 2)
  )

  val oo = IATestParams(
    ranges = Seq(
      range"(-3.0, 2.3).5",
      range"(4, 9)",
      range"(-7, -3)",
      range"(-5, 4)",
      range"(-3, 5)"
    ),
    bps = Seq(0, 5, 12, 10, 15),
    consts = Seq(-18.3, -4, 8, -20, 10, 5, 1.25, 1, 2)
  )

  def outputs(names: Seq[String], bp: Int) = {
    CustomBundle.withKeys(Output(Interval(range"[?, ?].$bp")), names)
  }

}

case class IATestParams(ranges: Seq[IntervalRange], bps: Seq[Int], consts: Seq[Double])