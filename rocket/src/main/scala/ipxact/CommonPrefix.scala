// See LICENSE for license details.

package ipxact

import java.lang.System.currentTimeMillis
import treadle.chronometry.Timer

object CommonPrefix {
  private val l = List.tabulate(1000000) { i => s"a_b_c_${i}"}

  def findPrefix(a: String, b: String): String = {
    a.zip(b).flatMap { case (a1, b1) => if(a1 == b1) Some(a1) else None }.mkString("")
  }

  /**
    * Faster way of finding common prefix between two strings
    * @param a first string
    * @param b seconds string
    * @return  common leading characters
    */
  def apply(a: String, b: String): String = {
    var index = 0
    val length = a.length min b.length
    while(index < length && a(index) == b(index)) { index += 1 }
    a.take(index)
  }

  def main(args: Array[String]): Unit = {
    val timer = new Timer

    for(i <- 0 until 10) {
      timer("zip method") {
        l.tail.foldLeft(l.head) { case (a, b) => findPrefix(a, b) }
      }

      timer("var method") {
        l.tail.foldLeft(l.head) { case (a, b) => apply(a, b) }
      }
    }

    println(timer.report())
  }

}
