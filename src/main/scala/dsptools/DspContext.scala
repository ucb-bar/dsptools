// See LICENSE for license details.

package dsptools

/** Different overflow handling methods */
trait OverflowType
case object Saturate extends OverflowType
case object Wrap extends OverflowType
case object Grow extends OverflowType

/** Different trim methods */
abstract class TrimType
case object Truncate extends TrimType
case object Round extends TrimType
case object NoTrim extends TrimType

object DspContext {
  val DefaultBinaryPoint = 15
  val DefaultNumberOfBits = 16
  val DefaultRegistersForFixedMultiply = 0
  val DefaultRegistersForFixedAdd     = 0
}
case class DspContext(
                     val overflowType: OverflowType = Wrap,
                     val trimType:                  TrimType     = NoTrim,
                     val binaryPoint:               Option[Int]  = Some(DspContext.DefaultBinaryPoint),
                     val numberOfBits:              Option[Int]  = Some(DspContext.DefaultNumberOfBits),
                     val use4Multiplies:            Boolean     = true,
                     val registersForFixedMultiply: Int         = DspContext.DefaultRegistersForFixedMultiply,
                     val registersForFixedAdd:      Int         = DspContext.DefaultRegistersForFixedAdd,
                     val multiplyBinaryPointGrowth: Int         = 1
                     ) {
}
