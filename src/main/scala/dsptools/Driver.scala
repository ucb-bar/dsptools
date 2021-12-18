// SPDX-License-Identifier: Apache-2.0

package dsptools

import chisel3._
import chisel3.iotesters.DriverCompatibility.ChiselExecutionResult
import chisel3.iotesters._
import firrtl.HasFirrtlOptions
import numbers.{DspRealFactory, TreadleDspRealFactory}
import firrtl_interpreter._

import scala.util.DynamicVariable

object Driver {

  private val optionsManagerVar = new DynamicVariable[Option[DspTesterOptionsManager]](None)
  def optionsManager = optionsManagerVar.value.getOrElse(new DspTesterOptionsManager)

  def execute[T <: MultiIOModule](dutGenerator: () => T,
      optionsManager: TesterOptionsManager)(testerGen: T => PeekPokeTester[T]): Boolean = {

    val om = optionsManager match {
      case d: DspTesterOptionsManager => Some(d)
      case _ => None
    }

    optionsManagerVar.withValue(om) {
      optionsManager.interpreterOptions = optionsManager.interpreterOptions.copy(
          blackBoxFactories = optionsManager.interpreterOptions.blackBoxFactories :+ new DspRealFactory
      )
      optionsManager.treadleOptions = optionsManager.treadleOptions.copy(
        blackBoxFactories = optionsManager.treadleOptions.blackBoxFactories :+ new TreadleDspRealFactory
      )
      iotesters.Driver.execute(dutGenerator, optionsManager)(testerGen)
    }

  }

  def execute[T <: MultiIOModule](dutGenerator: () => T,
      args: Array[String] = Array.empty)(testerGen: T => PeekPokeTester[T]): Boolean = {

    val optionsManager = new DspTesterOptionsManager {
      interpreterOptions = interpreterOptions.copy(
          blackBoxFactories = interpreterOptions.blackBoxFactories :+ new DspRealFactory
      )
      treadleOptions = treadleOptions.copy(
        blackBoxFactories = treadleOptions.blackBoxFactories :+ new TreadleDspRealFactory
      )
    }

    if (optionsManager.parse(args)) {
      execute(dutGenerator, optionsManager)(testerGen)
    } else {
      optionsManager.parser.showUsageAsError()
      false
    }
  }

  def executeFirrtlRepl[T <: MultiIOModule](dutGenerator: () => T,
      optionsManager: ReplOptionsManager = new ReplOptionsManager): Boolean = {

    optionsManager.chiselOptions = optionsManager.chiselOptions.copy(runFirrtlCompiler = false)
    optionsManager.firrtlOptions = optionsManager.firrtlOptions.copy(compilerName = "low")
    optionsManager.interpreterOptions = optionsManager.interpreterOptions.copy(
        blackBoxFactories = optionsManager.interpreterOptions.blackBoxFactories :+ new DspRealFactory
    )
    optionsManager.treadleOptions = optionsManager.treadleOptions.copy(
      blackBoxFactories = optionsManager.treadleOptions.blackBoxFactories :+ new TreadleDspRealFactory
    )

    logger.LoggerCompatibility.makeScope(optionsManager) {
      val chiselResult: ChiselExecutionResult = iotesters.DriverCompatibility.execute(optionsManager, dutGenerator)
      chiselResult match {
        case iotesters.DriverCompatibility.ChiselExecutionSuccess(_, emitted, _) =>
          optionsManager.replConfig = ReplConfig(firrtlSource = emitted)
          FirrtlRepl.execute(optionsManager)
          true
        case iotesters.DriverCompatibility.ChiselExecutionFailure(message) =>
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
    with HasTreadleOptions
