// See LICENSE for license details.

package dsptools

import chisel3.{Bits, Module}
import chisel3.core.{FixedPoint, SInt}
import chisel3.iotesters.{Backend, PeekPokeTester}

class DspTester[T <: Module](c: T) extends PeekPokeTester(c) {
  def poke(signal: FixedPoint, value: Double): Unit = {
    println(s"signal is $signal")
  }

}
