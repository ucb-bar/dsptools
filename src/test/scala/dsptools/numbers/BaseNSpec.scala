// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import dsptools.numbers.representations.BaseN
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BaseNSpec extends AnyFlatSpec with Matchers {
  behavior of "BaseN"
  it should "properly convert a decimal into BaseN" in {
    // n in decimal, rad = radix, res = expected representation in base rad
    case class BaseNTest(n: Int, rad: Int, res: Seq[Int])

    // Most significant digit first (matched against WolframAlpha)
    val tests = Seq(
      BaseNTest(27, 4, Seq(1, 2, 3)),
      BaseNTest(17, 3, Seq(1, 2, 2)),
      BaseNTest(37, 5, Seq(1, 2, 2))
    )
    tests foreach { case BaseNTest(n, rad, res) =>
      require(BaseN.toDigitSeqMSDFirst(n, rad) == res, s"Base $rad conversion should work!")
      val paddedBaseN = BaseN.toDigitSeqMSDFirst(n, rad, 500)
      require(paddedBaseN == (Seq.fill(paddedBaseN.length - res.length)(0) ++ res),
        s"Padded base $rad conversion should work!")
    }
  }
}

