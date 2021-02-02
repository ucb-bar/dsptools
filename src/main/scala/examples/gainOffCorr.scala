//// SPDX-License-Identifier: Apache-2.0
//
package dsptools.examples

import chisel3._

import spire.algebra.Ring
import spire.implicits._

//Simple implementation does the following
//   1.Input value can be either real/imag
//   2.Gain and offset either real/imag
//   3.Assuming the number of input sources = number of lanes for now
//   4.Assuming that the memory interface for gain and offset values will be done at a higher level

class gainOffCorr[T<:Data:Ring](genIn: => T,genGain: => T,genOff: => T,genOut: => T, numLanes: Int) extends Module {
    val io = IO(new Bundle {
       val inputVal =  Input(Vec(numLanes, genIn))
       val gainCorr =  Input(Vec(numLanes, genGain))
       val offsetCorr = Input(Vec(numLanes, genOff))
       val outputVal = Output(Vec(numLanes, genOut))
    })

    val inputGainCorr = io.inputVal.zip(io.gainCorr).map{case (in, gain) => in*gain }
    io.outputVal := inputGainCorr.zip(io.offsetCorr).map{case (inGainCorr, offset) => inGainCorr + offset }
}

