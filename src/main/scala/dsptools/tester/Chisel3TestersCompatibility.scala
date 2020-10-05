// SPDX-License-Identifier: Apache-2.0

package chisel3.iotesters

import chisel3._
import chisel3.experimental.DataMirror

// Bring out a bunch of private functions
object TestersCompatibility {

  // Stolen from chisel-testers hack
  private def extractElementBits(signal: Data): IndexedSeq[Element] = {
    signal match {
      case elt: Aggregate => elt.getElements.toIndexedSeq flatMap {extractElementBits(_)}
      case elt: Element => IndexedSeq(elt)
      case elt => throw new Exception(s"Cannot extractElementBits for type ${elt.getClass}")
    }
  }

  def flatten[T <: Aggregate](d: T): IndexedSeq[Bits] = extractElementBits(d) map { x => x.asInstanceOf[Bits]}

  def getModuleNames(mod: RawModule): Seq[(Element, String)] = {
    DataMirror.modulePorts(mod).flatMap(p => getDataNames(p._1, p._2))
  }
  def getDataNames(name: String, data: Data): Seq[(Element, String)] = {
    chisel3.iotesters.getDataNames(name, data)
  }
  def validName(name: String): String = chisel3.iotesters.validName(name)
  def bigIntToStr(x: BigInt, base: Int): String = chisel3.iotesters.bigIntToStr(x, base)
}



