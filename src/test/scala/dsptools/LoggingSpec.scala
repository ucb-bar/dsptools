// SPDX-License-Identifier: Apache-2.0

package dsptools

import chisel3._
import logger.{LazyLogging, LogLevel, Logger}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class DutWithLogging extends Module with LazyLogging {
  val io = IO(new Bundle {})

  logger.error("error level message")
  logger.warn("warn level message")
  logger.info("info level message")
  logger.debug("debug level message")
  logger.trace("trace level message")
}

class DutWithLoggingTester(c: DutWithLogging) extends DspTester(c)

class LoggingSpec extends AnyFreeSpec with Matchers {
  "logging can be emitted during hardware generation" - {
    "level defaults to warn" in {
      Logger.makeScope() {
        val captor = new Logger.OutputCaptor
        Logger.setOutput(captor.printStream)

        dsptools.Driver.execute(() => new DutWithLogging, Array.empty[String]) { c =>
          new DutWithLoggingTester(c)
        }
        captor.getOutputAsString should include("error level message")
        captor.getOutputAsString should include("warn level message")
        captor.getOutputAsString should not include ("info level message")
        captor.getOutputAsString should not include ("debug level message")
        captor.getOutputAsString should not include ("trace level message")
      }
    }
    "logging level can be set via command line args" in {
      Logger.makeScope() {
        val captor = new Logger.OutputCaptor
        Logger.setOutput(captor.printStream)

        dsptools.Driver.execute(() => new DutWithLogging, Array("--log-level", "info")) { c =>
          new DutWithLoggingTester(c)
        }
        captor.getOutputAsString should include("error level message")
        captor.getOutputAsString should include ("warn level message")
        captor.getOutputAsString should include ("info level message")
        captor.getOutputAsString should not include ("debug level message")
        captor.getOutputAsString should not include ("trace level message")
      }
    }
    "logging level can be set for a specific package" in {
      Logger.makeScope() {
        val captor = new Logger.OutputCaptor
        Logger.setOutput(captor.printStream)

        dsptools.Driver.execute(() => new DutWithLogging, Array("--class-log-level", "dsptools:warn")) { c =>
          new DutWithLoggingTester(c)
        }
        captor.getOutputAsString should include("error level message")
        captor.getOutputAsString should include ("warn level message")
        captor.getOutputAsString should not include ("info level message")
        captor.getOutputAsString should not include ("debug level message")
        captor.getOutputAsString should not include ("trace level message")
      }
    }
  }
}
