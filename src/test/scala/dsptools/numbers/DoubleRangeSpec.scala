// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class DoubleRangeSpec extends AnyFreeSpec with Matchers {
  "DecimalRange should" - {
    "work like basic Range" in {
      val doublesFromBigDecimals =  (BigDecimal(0.0) to 5.0 by 1.0).map(_.toDouble)
      val doubles = DoubleRange(0.0, 5.0).toSeq
      doublesFromBigDecimals should contain theSameElementsInOrderAs(doubles)
    }

    "allow backward traverse" in {
      val doublesFromBigDecimals =  (BigDecimal(10.0) to 5.0 by -1.0).map(_.toDouble)
      val doubles = DoubleRange(10.0, 5.0, -1.0).toSeq
      doublesFromBigDecimals should contain theSameElementsInOrderAs(doubles)
    }

    "not allow step of zero" in {
      intercept[AssertionError] {
        DoubleRange(1.0, 7.0, 0.0)
      }
    }
  }

}
