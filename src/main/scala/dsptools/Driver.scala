// See LICENSE for license details.

package dsptools

import java.io.PrintWriter

import chisel3._
import chisel3.iotesters._
import dsptools.numbers.resizer.{BitReducer, ChangeWidthTransform}
import firrtl._
import numbers.DspRealFactory
import firrtl_interpreter._

import scala.util.DynamicVariable

object Driver {

  private val optionsManagerVar = new DynamicVariable[Option[DspTesterOptionsManager]](None)
  def optionsManager = optionsManagerVar.value.getOrElse(new DspTesterOptionsManager)

  def execute[T <: Module](dutGenerator: () => T,
      optionsManager: TesterOptionsManager)(testerGen: T => PeekPokeTester[T]): Boolean = {

    val om = optionsManager match {
      case d: DspTesterOptionsManager => Some(d)
      case _ => None
    }

    optionsManagerVar.withValue(om) {
      optionsManager.interpreterOptions = optionsManager.interpreterOptions.copy(
          blackBoxFactories = optionsManager.interpreterOptions.blackBoxFactories :+ new DspRealFactory)
      iotesters.Driver.execute(dutGenerator, optionsManager)(testerGen)
    }

  }

  def execute[T <: Module](dutGenerator: () => T,
      args: Array[String] = Array.empty)(testerGen: T => PeekPokeTester[T]): Boolean = {

    val optionsManager = new DspTesterOptionsManager {
      interpreterOptions = interpreterOptions.copy(
          blackBoxFactories = interpreterOptions.blackBoxFactories :+ new DspRealFactory)
    }

    if (optionsManager.parse(args)) execute(dutGenerator, optionsManager)(testerGen)
    else {
      optionsManager.parser.showUsageAsError()
      false
    }
  }

  //scalastyle:off method.length
  def executeWithBitReduction[T <: Module](dutGenerator: () => T,
                                           optionsManager: TesterOptionsManager)(testerGen: T => PeekPokeTester[T]): Boolean = {

    val om = optionsManager match {
      case d: DspTesterOptionsManager => Some(d)
      case _ => None
    }

    optionsManagerVar.withValue(om) {
      optionsManager.interpreterOptions = optionsManager.interpreterOptions.copy(
        blackBoxFactories = optionsManager.interpreterOptions.blackBoxFactories :+ new DspRealFactory)

      val requestedName = optionsManager.interpreterOptions.monitorReportFileName

      optionsManager.interpreterOptions = optionsManager.interpreterOptions.copy(
        monitorBitUsage = true,
        monitorReportFileName = "signal-bitsizes.csv",
        prettyPrintReport = false
      )
      val passed = iotesters.Driver.execute(dutGenerator, optionsManager)(testerGen)

      val reportFileName = optionsManager.interpreterOptions.getMonitorReportFile(optionsManager)

      if(requestedName.nonEmpty) {
        println(s"Warning: ignoring monitorReportFileName=$requestedName, using: $reportFileName")
      }

      val data = io.Source.fromFile(reportFileName).getLines().toList.drop(1)

      val im = new BitReducer(data)
      im.run()
      val report = im.getReportString
      println(report)

      val annotationMap = im.getAnnotationMap
      annotationMap.annotations.foreach { anno =>
        println(anno.serialize)
      }

      val firrtlFilename = optionsManager.firrtlOptions.getTargetFile(optionsManager)
      val firrtlString = io.Source.fromFile(firrtlFilename).getLines().mkString("\n")

      println("Firrtl:\n" + firrtlString)


      val circuitState = firrtl.CircuitState(Parser.parse(firrtlString), LowForm, Some(annotationMap))

      val transform = new ChangeWidthTransform

      val newCircuitState = transform.execute(circuitState)

      val newFirrtlString = newCircuitState.circuit.serialize

      println("Bit-reduced Firrtl\n" + newFirrtlString)

      val newFirrtlFileName = {
        optionsManager.firrtlOptions.getTargetFile(optionsManager).replaceFirst(""".lo.fir$""", ".bit-reduced.fir")
      }
      val writer = new PrintWriter(newFirrtlFileName)
      writer.write(newFirrtlString)
      writer.close()

      optionsManager.interpreterOptions = optionsManager.interpreterOptions.copy(
        monitorBitUsage = true,
        monitorReportFileName = "signal-bitsizes-2.csv",
        prettyPrintReport = false
      )
      val passed2 = iotesters.Driver.execute(
        dutGenerator,
        optionsManager,
        Some(newFirrtlString)
      )(testerGen)

      val reportFileName2 = optionsManager.interpreterOptions.getMonitorReportFile(optionsManager)

      val data2 = io.Source.fromFile(reportFileName2).getLines().toList.drop(1)

      val im2 = new BitReducer(data2)
      im2.run()
      val report2 = im2.getReportString
      println(report2)

      passed & passed2
    }
  }

  def executeWithBitReduction[T <: Module](
                                            dutGenerator: () => T,
                                            args: Array[String] = Array.empty
                                          )
                                          (
                                            testerGen: T => PeekPokeTester[T]
                                          ): Boolean = {

    val optionsManager = new DspTesterOptionsManager {
      interpreterOptions = interpreterOptions.copy(
        blackBoxFactories = interpreterOptions.blackBoxFactories :+ new DspRealFactory)
    }

    if (optionsManager.parse(args)) {
      executeWithBitReduction(dutGenerator, optionsManager)(testerGen)
    }
    else {
      optionsManager.parser.showUsageAsError()
      false
    }
  }


  def executeFirrtlRepl[T <: Module](dutGenerator: () => T,
      optionsManager: ReplOptionsManager = new ReplOptionsManager): Boolean = {

    optionsManager.chiselOptions = optionsManager.chiselOptions.copy(runFirrtlCompiler = false)
    optionsManager.firrtlOptions = optionsManager.firrtlOptions.copy(compilerName = "low")
    optionsManager.interpreterOptions = optionsManager.interpreterOptions.copy(
        blackBoxFactories = optionsManager.interpreterOptions.blackBoxFactories :+ new DspRealFactory)

    logger.Logger.makeScope(optionsManager) {
      val chiselResult: ChiselExecutionResult = chisel3.Driver.execute(optionsManager, dutGenerator)
      chiselResult match {
        case ChiselExecutionSuccess(_, emitted, _) =>
          optionsManager.replConfig = ReplConfig(firrtlSource = emitted)
          FirrtlRepl.execute(optionsManager)
          true
        case ChiselExecutionFailure(message) =>
          println("Failed to compile circuit")
          false
      }
    }
  }

}

class ReplOptionsManager
  extends InterpreterOptionsManager
    with HasInterpreterOptions
    with HasChiselExecutionOptions
    with HasFirrtlOptions
    with HasReplConfig
