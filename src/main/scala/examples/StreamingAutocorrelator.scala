// See LICENSE for license details.

package examples

import chisel3.{Bundle, Data}
import chisel3.core.{Wire, Reg, Vec, Module}
import dsptools.{Saturate, DspContext}
import dsptools.examples.TransposedStreamingFIR
import spire.algebra.Ring

class StreamingAutocorrelator[T <: Data](inputGenerator: => T, outputGenerator: => T, delay: Int, windowSize: Int)
                                        (implicit ev : DspContext => Ring[T],
                                         val context: DspContext) extends Module {
  implicit val ev2 = ev(context)
  val io = new Bundle {
    val input = inputGenerator
    val output = outputGenerator
  }

  // create a sequence of registers (head is io.input)
  val delays = (0 until delay + windowSize).scanLeft(io.input) { case (left, _) =>
    val nextReg = Reg(inputGenerator)
    nextReg := left
    nextReg
  }

  val window = delays.drop(delay + 1).reverse

  val firFilter = {
    val newContext = context.copy(overflowType = Saturate)

    Module(new TransposedStreamingFIR(inputGenerator, outputGenerator, inputGenerator, windowSize))
  }

  firFilter.io.taps := window
  firFilter.io.input := io.input
  io.output := firFilter.io.output
}
