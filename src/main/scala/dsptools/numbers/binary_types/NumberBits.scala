// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3._

trait RealBits[A <: Data] extends Any with Real[A] with ChiselConvertableFrom[A] with BinaryRepresentation[A] {
}

object RealBits {
  def apply[A <: Data](implicit A: RealBits[A]): RealBits[A] = A
}

trait IntegerBits[A <: Data] extends Any with RealBits[A] with Integer[A] {
}

object IntegerBits {
  def apply[A <: Data](implicit A: IntegerBits[A]): IntegerBits[A] = A
}