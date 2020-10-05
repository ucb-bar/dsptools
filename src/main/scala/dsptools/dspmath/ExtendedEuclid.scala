// SPDX-License-Identifier: Apache-2.0

package dsptools.dspmath

object ExtendedEuclid {
  /** Extended Euclidean Algorithm
    * ax + by = gcd(a, b)
    * Inputs: a, b
    * Outputs: gcd, x, y
    */
  def egcd(a: Int, b: Int): (Int, Int, Int) = {
    if (a == 0) {
      (b, 0, 1)
    } else {
      val (gcd, y, x) = egcd(b % a, a)
      (gcd, x - (b / a) * y, y)
    }
  }
}