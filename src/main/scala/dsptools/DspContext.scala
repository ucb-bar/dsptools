// See LICENSE for license details.

package dsptools

import scala.util.DynamicVariable

/** Different overflow handling methods */
trait OverflowType
case object Saturate extends OverflowType
case object Wrap extends OverflowType
case object Grow extends OverflowType

/** Different trim methods */
abstract class TrimType
case object Truncate extends TrimType
case object RoundHalfUp extends TrimType
case object NoTrim extends TrimType
case object Floor extends TrimType

object DspContext {
  val DefaultOverflowType              = Grow
  val DefaultBinaryPoint               = 14
  val DefaultNumBits              = 16
  val DefaultRegistersForFixedMultiply = 0
  val DefaultRegistersForFixedAdd      = 0

  private val dynamicDspContextVar = new DynamicVariable[DspContext](new DspContext())

  def current: DspContext = dynamicDspContextVar.value
  def alter[T](newContext: DspContext)(blk: => T): T = {
    dynamicDspContextVar.withValue(newContext) {
      blk
    }
  }

  def withBinaryPoint[T](newBinaryPoint: Int)(blk: => T): T = {
    dynamicDspContextVar.withValue(current.copy(binaryPoint = Some(newBinaryPoint))) {
      blk
    }
  }
  def withNumBits[T](newNumberOfBits: Int)(blk: => T): T = {
    dynamicDspContextVar.withValue(current.copy(numBits = Some(newNumberOfBits))) {
      blk
    }
  }

  def withComplexUse4Muls[T](newUse4Multiples: Boolean)(blk: => T): T = {
    dynamicDspContextVar.withValue(current.copy(complexUse4Muls = newUse4Multiples)) {
      blk
    }
  }
  def withOverflowType[T](newOverflowType: OverflowType)(blk: => T): T = {
    dynamicDspContextVar.withValue(current.copy(overflowType = newOverflowType)) {
      blk
    }
  }
  def withTrimType[T](newTrimType: TrimType)(blk: => T): T = {
    dynamicDspContextVar.withValue(current.copy(trimType = newTrimType)) {
      blk
    }
  }

  def withBinaryPointGrowth[T](newBinaryPointGrowth: Int)(blk: => T): T = {
    dynamicDspContextVar.withValue(current.copy(binaryPointGrowth = newBinaryPointGrowth)) {
      blk
    }
  }

  //withNumMulPipes
  //withNumAddPipes

}

trait hasContext extends Any {
  def context: DspContext = DspContext.current
}

case class DspContext(
    val overflowType:              OverflowType = DspContext.DefaultOverflowType,
    val trimType:                  TrimType     = NoTrim,
    val binaryPoint:               Option[Int]  = Some(DspContext.DefaultBinaryPoint),
    val numBits:              Option[Int]  = Some(DspContext.DefaultNumBits),
    val complexUse4Muls:     Boolean      = true,
    val numMulPipes: Int          = DspContext.DefaultRegistersForFixedMultiply,
    val numAddPipes:      Int          = DspContext.DefaultRegistersForFixedAdd,
    val binaryPointGrowth: Int          = 1){
  require(binaryPointGrowth >= 0, "Binary point growth must be non-negative")
// complexMulDly
  // adde mul delay for complex = add,mul, 4,mull -- see that it changes
}
