package dsptools.intervals
import chisel3.internal.firrtl.IntervalRange
import firrtl.ir.Closed

object IAUtility {

  /** Expand range by n (twice of `halfn`). If n is negative, shrink. */
  def expandBy(range: IntervalRange, halfn: Double) = {
    val newMinT = getMin(range) - halfn
    val newMax = getMax(range) + halfn
    val newMin = if (newMinT > newMax) newMax else newMinT
    if (newMinT > newMax) println("Attempting to shrink range too much!")
    /*
    println(
      (if (halfn < 0) s"[shrink $halfn]: " else s"[expand $halfn]: ") +
        s"old min: ${getMin(range)} old max: ${getMax(range)}; " +
        s"new min: $newMin new max: $newMax"
    )
    */
    IntervalRange(Closed(newMin), Closed(newMax), range.binaryPoint)
  }

  /** Shift range to the right by n. If n is negative, shift left.  */
  def shiftRightBy(range: IntervalRange, n: Double) = {
    val newMin = getMin(range) + n
    val newMax = getMax(range) + n
    IntervalRange(Closed(newMin), Closed(newMax), range.binaryPoint)
  }

  /** Check if range contains negative numbers. */
  def containsNegative(range: IntervalRange) = getMin(range) < 0

  /** Get the # of bits required to represent the integer portion of the
    * rounded bounds (including sign bit if necessary).
    */
  def getIntWidth(range: IntervalRange) = {
    // TODO: require(range.binaryPoint.get == 0, "getIntWidth only works for bp = 0")
    val min = BigInt(math.round(getMin(range)).toInt)
    val max = BigInt(math.round(getMax(range)).toInt)
    val minWidth = if (min < 0) min.bitLength + 1 else min.bitLength
    val maxWidth = if (max < 0) max.bitLength + 1 else max.bitLength
    math.max(minWidth, maxWidth)
  }

  /** Gets min */
  def getMin(range: IntervalRange) = range.getPossibleValues.min
  /** Gets max */
  def getMax(range: IntervalRange) = range.getPossibleValues.max
  /** Gets width of range */
  def getRange(range: IntervalRange) = getMax(range) - getMin(range)

}