package chisel3.iotesters

import chisel3._

// Bring out a bunch of private functions
object TestersCompatibility {
  def flatten[T <: Aggregate](d: T): IndexedSeq[Bits] = d.flatten
  def getDataNames(name: String, data: Data): Seq[(Element, String)] = {
    chisel3.iotesters.getDataNames(name, data)
  }
  def validName(name: String): String = chisel3.iotesters.validName(name)
  def bigIntToStr(x: BigInt, base: Int): String = chisel3.iotesters.bigIntToStr(x, base)
}



