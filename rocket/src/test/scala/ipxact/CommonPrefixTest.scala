// See LICENSE for license details.

package ipxact

import logger.LazyLogging
import org.scalatest.{FreeSpec, Matchers}

class CommonPrefixTest extends FreeSpec with Matchers with LazyLogging {
  private val l = List.tabulate(1000000) { i => s"a_b_c_$i"}

  "optimized prefix matcher should be 10 times faster than collections style" in {

    for(_ <- 0 until 3) {
      val zipStart = System.nanoTime()
        l.tail.foldLeft(l.head) { case (a, b) => CommonPrefix.findPrefix(a, b) }
      val zipEnd = System.nanoTime()


      val applyStart = System.nanoTime()
        l.tail.foldLeft(l.head) { case (a, b) => CommonPrefix(a, b) }
      val applyEnd = System.nanoTime()

      val zipElapsed = zipEnd - zipStart
      val applyElapsed = applyEnd - applyStart

      logger.info(
        f"zipElapsed = ${zipElapsed.toDouble / 1000000000.0}%7.4f," +
          f" applyElapsed = ${applyElapsed.toDouble / 1000000000.0}%7.4f"
      )
      (zipElapsed > applyElapsed * 10) should be (true)
    }
  }

}
