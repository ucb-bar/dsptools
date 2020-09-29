// SPDX-License-Identifier: Apache-2.0

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
case object NoTrim extends TrimType
case object RoundDown extends TrimType
case object RoundUp extends TrimType
case object RoundTowardsZero extends TrimType
case object RoundTowardsInfinity extends TrimType
case object RoundHalfDown extends TrimType
case object RoundHalfUp extends TrimType
case object RoundHalfTowardsZero extends TrimType
case object RoundHalfTowardsInfinity extends TrimType
case object RoundHalfToEven extends TrimType
case object RoundHalfToOdd extends TrimType

object DspContext {

  val defaultOverflowType = Grow
  val defaultTrimType = RoundDown
  val defaultBinaryPointGrowth = 1
  val defaultBinaryPoint = Some(14)
  val defaultNumBits = Some(16)
  val defaultComplexUse4Muls = false
  val defaultNumMulPipes = 0
  val defaultNumAddPipes = 0

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

  def withNumBits[T](newNumBits: Int)(blk: => T): T = {
    dynamicDspContextVar.withValue(current.copy(numBits = Some(newNumBits))) {
      blk
    }
  }

  def withComplexUse4Muls[T](newComplexUse4Muls: Boolean)(blk: => T): T = {
    dynamicDspContextVar.withValue(current.copy(complexUse4Muls = newComplexUse4Muls)) {
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

  def withNumMulPipes[T](newNumMulPipes: Int)(blk: => T): T = {
    dynamicDspContextVar.withValue(current.copy(numMulPipes = newNumMulPipes)) {
      blk
    }
  }

  def withNumAddPipes[T](newNumAddPipes: Int)(blk: => T): T = {
    dynamicDspContextVar.withValue(current.copy(numAddPipes = newNumAddPipes)) {
      blk
    }
  }

}

trait hasContext extends Any {
  def context: DspContext = DspContext.current
}

case class DspContext(
    val overflowType: OverflowType = DspContext.defaultOverflowType,
    val trimType: TrimType = DspContext.defaultTrimType,
    val binaryPoint: Option[Int]  = DspContext.defaultBinaryPoint,
    val numBits: Option[Int]  = DspContext.defaultNumBits,
    val complexUse4Muls: Boolean = DspContext.defaultComplexUse4Muls,
    val numMulPipes: Int = DspContext.defaultNumMulPipes,
    val numAddPipes: Int = DspContext.defaultNumAddPipes,
    val binaryPointGrowth: Int = DspContext.defaultBinaryPointGrowth) {

  require(numMulPipes >= 0, "# of pipeline registers for multiplication must be >= 0 ")
  require(numAddPipes >= 0, "# of pipeline registers for addition must be >= 0 ")
  require(binaryPointGrowth >= 0, "Binary point growth must be non-negative")
  numBits match {
    case Some(i) => require(i > 0, "# of bits must be > 0")
    case _ =>
  }

  def complexMulPipe: Int = {
    if (complexUse4Muls) numMulPipes + numAddPipes
    else (2 * numAddPipes) + numMulPipes
  }
}
