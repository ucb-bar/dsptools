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

import dsptools.numbers.representations.MixedRadix
import org.scalatest.{FlatSpec, Matchers}

class MixedRadixSpec extends FlatSpec with Matchers {
  behavior of "MixedRadix"
  it should "properly convert a decimal into MixedRadix" in {
    // n in decimal, rad = digit radices, res = expected representation
    case class MixedRadixTest(n: Int, rad: Seq[Int], res: Seq[Int])

    // Most significant digit first (matched against WolframAlpha)
    val tests = Seq(
      MixedRadixTest(6, Seq(1, 1, 4, 4, 2), Seq(3, 0)),
      MixedRadixTest(6, Seq(1, 1, 4, 4, 2, 4), Seq(1, 2))
    )
    tests foreach { case MixedRadixTest(n, rad, res) =>
      require(MixedRadix.toDigitSeqMSDFirst(n, rad) == res, s"$rad conversion should work!")
      val paddedMixedRadix = MixedRadix.toDigitSeqMSDFirst(n, rad, 16)
      require(paddedMixedRadix == Seq.fill(paddedMixedRadix.length - res.length)(0) ++ res,
        s"Padded $rad conversion should work!")
    }
  }
}
