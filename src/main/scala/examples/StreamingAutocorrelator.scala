// SPDX-License-Identifier: Apache-2.0

package examples

import chisel3._
import dsptools.{hasContext, DspContext, Grow}
import dsptools.examples.TransposedStreamingFIR
import spire.algebra.Ring

class StreamingAutocorrelator[T <: Data:Ring](inputGenerator: => T, outputGenerator: => T, delay: Int, windowSize: Int)
                                         extends Module with hasContext {
//  implicit val ev2 = ev(context)
  val io = IO(new Bundle {
    val input = Input(inputGenerator)
    val output = Output(outputGenerator)
  })

  // create a sequence of registers (head is io.input)
  val delays = (0 until delay + windowSize).scanLeft(io.input) { case (left, _) =>
    val nextReg = Reg(inputGenerator)
    nextReg := left
    nextReg
  }

  val window = delays.drop(delay + 1).reverse

  val firFilter = DspContext.withOverflowType(Grow) {
    Module(new TransposedStreamingFIR(inputGenerator, outputGenerator, inputGenerator, windowSize))
  }

  firFilter.io.taps := window
  firFilter.io.input := io.input
  io.output := firFilter.io.output
}
