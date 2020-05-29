// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import chisel3.experimental._
import dsptools.intervals.IAUtility
import org.scalatest.{FreeSpec, Matchers}

class IAUtilitySpec extends FreeSpec with Matchers {
  "getIntWidth tests" in {
    val r1 = range"[-1.5,2.5].1"

    val intWidth = IAUtility.getIntWidth(r1)
    println(s"range is $intWidth")
  }


}
