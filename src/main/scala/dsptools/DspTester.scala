// See LICENSE for license details.

package dsptools

import chisel3.Module
import chisel3.core.SInt
import chisel3.iotesters.{Backend, PeekPokeTester}

class DspTester[T <: Module](c: T, b: Option[Backend] = None) extends PeekPokeTester(c, _backend = b) {

}
