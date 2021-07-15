// See LICENSE for license details.

package ipxact

import java.lang.System.currentTimeMillis
import treadle.chronometry.Timer

object CommonPrefix {
  /** Collection style method to find common prefixes at head of tring
    *
    * @param a  string 1
    * @param b  string 2
    * @return
    */
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
}
