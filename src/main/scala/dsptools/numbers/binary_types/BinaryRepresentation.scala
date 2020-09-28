// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3.{Data, UInt, Bool}

object BinaryRepresentation {
  def apply[A <: Data](implicit A: BinaryRepresentation[A]): BinaryRepresentation[A] = A
}

trait BinaryRepresentation[A <: Data] extends Any {
  def shl(a: A, n: Int): A
  def shl(a: A, n: UInt): A
  // For negative signed #'s, this is actually round to negative infinity
  def shr(a: A, n: Int): A
  def shr(a: A, n: UInt): A
  def signBit(a: A): Bool

  // Rounds to zero (positive, negative consistent!)
  // Divide/multiply by 2^n
  def div2(a: A, n: Int): A = shr(a, n)
  def mul2(a: A, n: Int): A = shl(a, n)
  // Trim to n fractional bits (with DspContext) -- doesn't affect DspReal
  def trimBinary(a: A, n: Int): A = trimBinary(a, Some(n))
  def trimBinary(a: A, n: Option[Int]): A

  // Clip A to B (range)
  def clip(a: A, b: A): A
}
