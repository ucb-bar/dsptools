// See LICENSE for license details.

package dsptools

import chisel3._
import chisel3.iotesters._
import firrtl.{HasFirrtlOptions, ExecutionOptionsManager}
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

  def executeFirrtlRepl[T <: Module](dutGenerator: () => T,
      optionsManager: ReplOptionsManager = new ReplOptionsManager): Boolean = {

    optionsManager.chiselOptions = optionsManager.chiselOptions.copy(runFirrtlCompiler = false)
    optionsManager.firrtlOptions = optionsManager.firrtlOptions.copy(compilerName = "low")
    optionsManager.interpreterOptions = optionsManager.interpreterOptions.copy(
        blackBoxFactories = optionsManager.interpreterOptions.blackBoxFactories :+ new DspRealFactory)

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

class ReplOptionsManager
  extends ExecutionOptionsManager("dsptools-repl")
    with HasInterpreterOptions
    with HasChiselExecutionOptions
    with HasFirrtlOptions
    with HasReplConfig
