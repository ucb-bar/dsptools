package dsptools.numbers

import chisel3.Data

object ConvertableTo {
  def apply[A <: Data](implicit A: ConvertableTo[A]): ConvertableTo[A] = A
}