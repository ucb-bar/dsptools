// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3.Data

object ConvertableTo {
  def apply[A <: Data](implicit A: ConvertableTo[A]): ConvertableTo[A] = A
}

trait ConvertableTo[A <: Data] extends Any with spire.math.ConvertableTo[A] {
  def fromDouble(d: Double, a: A): A
  def fromDoubleWithFixedWidth(d: Double, a: A): A
}