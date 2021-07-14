// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

/** Convenience method to provide range of doubles
  * Provides behavior in scala 2.13 that matches 2.12
  * Supports reverse ranges
  *
  * @param start first number to be returned
  * @param stop  either stop at or stop before depending on isTo
  * @param step  increment
  * @param isTo
  */
case class DoubleRange(start: Double, stop: Double, step: Double = 1.0, isTo: Boolean = true) extends Iterator[Double] {
  assert(step != 0.0)
  var current = start
  val runningBackwards = step < 0.0

  override def hasNext: Boolean = {
    if(runningBackwards) {
      (isTo && current >= stop) || current > stop
    } else {
      (isTo && current <= stop) || current < stop
    }
  }

  override def next(): Double = {
    val returnValue = current
    current += step
    returnValue
  }
}

object DoubleRangeTo {
  def apply(start: Double, stop: Double, step: Double = 1.0): DoubleRange = {
    DoubleRange(start, stop, step)
  }
}

object DoubleRangeUntil {
  def apply(start: Double, stop: Double, step: Double = 1.0): DoubleRange = {
    DoubleRange(start, stop, step, isTo = false)
  }
}
