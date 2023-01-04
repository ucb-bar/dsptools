// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3.experimental.FixedPoint
import chisel3.{Data, SInt}
import dsptools.DspException

object ChiselConvertableFrom {
  def apply[A <: Data](implicit A: ChiselConvertableFrom[A]): ChiselConvertableFrom[A] = A
}

trait ChiselConvertableFrom[A <: Data] extends Any {
  def intPart(a: A): SInt

  def asFixed(a: A, proto: FixedPoint): FixedPoint
  def asFixed(a: A): FixedPoint = throw DspException("As fixed needs prototype argument!")

  def asReal(a: A): DspReal
}
