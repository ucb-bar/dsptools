// See LICENSE for license details.

package dsptools.numbers

import chisel3.core.SInt
import dsptools.{Grow, DspContext}
import spire.algebra.Ring

/**
  * Defines basic math functions for SInt
  * @param context a context object describing SInt behavior
  */
class SIntRing(implicit context: DspContext) extends Ring[SInt] {
  def plus(f: SInt, g: SInt): SInt = {
    if(context.overflowType == Grow) {
      f +& g
    }
    else {
      f +% g
    }
  }
  def times(f: SInt, g: SInt): SInt = {
    f * g
  }
  def one: SInt = SInt(value = BigInt(1))
  def zero: SInt = SInt(value = BigInt(0))
  def negate(f: SInt): SInt = zero - f
}
