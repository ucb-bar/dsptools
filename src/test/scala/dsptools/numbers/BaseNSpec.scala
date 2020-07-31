/*
Copyright 2020 The Regents of the University of California (Regents)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package dsptools.numbers

import dsptools.numbers.representations.BaseN
import org.scalatest.{FlatSpec, Matchers}

class BaseNSpec extends FlatSpec with Matchers {
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

