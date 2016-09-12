// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import dsptools.{DspContext, Grow}
import spire.algebra.Ring

/**
  * Defines basic math functions for FixedPoint numbers
  * @param context a context object describing SInt behavior
  */
class FixedPointRing(implicit context: DspContext) extends Ring[FixedPoint] {
  def plus(f: FixedPoint, g: FixedPoint): FixedPoint = {
    if(context.overflowType == Grow) {
      f +& g
    }
    else {
      f +% g
    }
  }
  def times(f: FixedPoint, g: FixedPoint): FixedPoint = {
    f * g
  }
  def one: FixedPoint = FixedPoint.fromBigInt(BigInt(1), binaryPoint = 0)
  def zero: FixedPoint = FixedPoint.fromBigInt(BigInt(0), binaryPoint = 0)
  def negate(f: FixedPoint): FixedPoint = zero - f
}


