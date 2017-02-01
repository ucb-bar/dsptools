package dsptools.numbers

import chisel3.{Data, UInt, Bool}

object ChiselBaseNum {
  def apply[A <: Data](implicit A: ChiselBaseNum[A]): ChiselBaseNum[A] = A
}

trait ChiselBaseNum[A <: Data] extends Any {
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
}