package dsptools.numbers

import chisel3.Data

object ChiselBaseNum {
  def apply[A <: Data](implicit A: ChiselBaseNum[A]): ChiselBaseNum[A] = A
}

trait ChiselBaseNum[A <: Data] extends Any {
  def shl(a: A, n: Int): A
  def shl(a: A, n: UInt): A
  def shr(a: A, n: Int): A
  def shr(a: A, n: UInt): A
  def signBit(a: A): Bool
}