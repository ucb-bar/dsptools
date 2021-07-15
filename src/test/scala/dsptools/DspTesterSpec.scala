// SPDX-License-Identifier: Apache-2.0

package dsptools

import DspTesterUtilities._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.math.{pow, abs}

class DspTesterSpec {

}

class DspTesterUtilitiesSpec extends AnyFlatSpec with Matchers {

  behavior of "Tester Converters"

  it should "convert positive and negative doubles to their BigInt, fixed point equivalents" in {

    def check_conversion(value: Double, totalWidth: Int, fractionalWidth: Int, verbose: Boolean = false): Unit = {
      if (verbose) { println(s"value = $value\ntotal width = $totalWidth\nfractional width = $fractionalWidth") }
      var bi = signedToBigIntUnsigned(value, totalWidth, fractionalWidth)
      if (verbose) { println(s"result = $bi") }
      // check sign, flip if necessary
      if (totalWidth > 0 && bi.testBit(totalWidth-1)) {
        bi = -1 * ((bi ^ ((BigInt(1) << totalWidth) - 1)) + 1)
      }
      val bid = bi.toDouble / (BigInt(1) << fractionalWidth).toDouble
      if (verbose) { println(s"back to double = $bid") }
      val comp = scala.math.abs(bid-value)
      if (verbose) { println(s"comp = $comp") }
      val ref = scala.math.pow(2, -fractionalWidth)
      if (verbose) { println(s"ref = $ref") }
      require(abs(bid-value) < pow(2, -fractionalWidth))
    }

    // integers
    var width = 14
    for (i <- -pow(2,width-1).toInt until pow(2,width-1).toInt) {
      check_conversion(i, width, 0)
    }

    // big integers
    width = 40
    for (i <- -pow(2,width-1).toInt to pow(2,width-1).toInt by pow(2, 20).toInt) {
      check_conversion(i, width, 0)
    }

    // total > fractional
    width = 19
    var fract = 8
    for (i <- BigDecimal(-pow(2,width-fract-1)) to pow(2,width-fract-1)-1 by 1.0/fract*0.9) {
      check_conversion(i.toDouble, width, fract)
    }

    // total < fractional
    width = 11
    fract = 17
    for (i <- BigDecimal(-pow(2,width-fract-1)) to pow(2,width-fract-1)-1 by 1.0/fract*0.9) {
      check_conversion(i.toDouble, width, fract)
    }

  }

  it should "fail to convert doubles to BigInts when not enough space is supplied" in {
    intercept[IllegalArgumentException] { signedToBigIntUnsigned(2.0, 4, 2) }
    intercept[IllegalArgumentException] { signedToBigIntUnsigned(-2.25, 4, 2) }
  }

}
