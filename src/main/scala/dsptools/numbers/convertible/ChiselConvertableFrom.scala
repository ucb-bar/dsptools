package dsptools.numbers

import chisel3.{Data, SInt, FixedPoint, DspReal}

object ChiselConvertableFrom {
  def apply[A <: Data](implicit A: ChiselConvertableFrom[A]): ChiselConvertableFrom[A] = A
}

trait ChiselConvertableFrom[A <: Data] extends Any {
  def intPart(a: A): SInt
  def asFixed(a: A, proto: FixedPoint): FixedPoint
  def asReal(a: A): DspReal
}