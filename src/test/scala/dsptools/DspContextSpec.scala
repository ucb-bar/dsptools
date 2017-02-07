// See LICENSE for license details.

package dsptools

import org.scalatest.{Matchers, FreeSpec}

//scalastyle:off magic.number

class DspContextSpec extends FreeSpec with Matchers {
  "Context handling should be unobtrusive and convenient" - {
    "There should be a default available at all times" in {
      DspContext.current.binaryPoint should be (Some(DspContext.DefaultBinaryPoint))
    }

    "it can be very to override for simple alterations" in {
      DspContext.current.binaryPoint should be (Some(DspContext.DefaultBinaryPoint))

      DspContext.withBinaryPoint(-22) {
        DspContext.current.binaryPoint.get should be (-22)
      }

      DspContext.current.binaryPoint should be (Some(DspContext.DefaultBinaryPoint))
    }

    "it should be easy to override when using multiples" in {
      DspContext.current.binaryPoint should be (Some(DspContext.DefaultBinaryPoint))
      DspContext.current.overflowType should be (DspContext.DefaultOverflowType)

      DspContext.alter(DspContext.current.copy(binaryPoint = Some(77), overflowType = Saturate)) {
        DspContext.current.binaryPoint.get should be (77)
        DspContext.current.overflowType should be (Saturate)
      }

      DspContext.current.binaryPoint should be (Some(DspContext.DefaultBinaryPoint))
      DspContext.current.overflowType should be (DspContext.DefaultOverflowType)
    }

    "it should work multi-threaded and return values of block" in {
      DspContext.current.numBits should be (Some(DspContext.DefaultNumBits))

      val points = (0 to 100).toParArray.map { n =>
        DspContext.withNumBits(n) {
          DspContext.current.numBits.get should be (n)
          n * n
        }
      }
      points.zipWithIndex.foreach { case (p: Int, i: Int) => p should be (i * i)}

      DspContext.current.numBits should be (Some(DspContext.DefaultNumBits))
    }
  }
}
