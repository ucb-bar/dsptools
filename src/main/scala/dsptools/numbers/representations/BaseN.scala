// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers.representations

object BaseN {

  /** Converts a decimal representation of the number n into a Seq of
    * Ints representing the base-r interpretation of n
    * NOTE: Least significant digit is highest indexed (right-most) due to recursion
    */
  private def toDigitSeqInternal(n: Int, r: Int): Seq[Int] = {
    require(n >= 0, "n must be >= 0")
    require(r > 0, s"r $r must be > 0")
    // Least significant digit is right-most (resolved in this iteration)
    if (n == 0) Nil else toDigitSeqInternal(n / r, r) :+ (n % r)
  }

  /** Base-r representation of n, most significant digit 0-indexed */
  def toDigitSeqMSDFirst(n: Int, r: Int): Seq[Int] = {
    // Highest digit first (left-most)
    val temp = toDigitSeqInternal(n, r)
    // Should return non-empty list
    if (temp.isEmpty) Seq(0) else temp
  }
  /** Zero pads Seq[Int] base-r representation */
  def toDigitSeqMSDFirst(n: Int, r: Int, maxn: Int): Seq[Int] = {
    val digitSeq = toDigitSeqMSDFirst(n, r)
    val maxNumDigits = toDigitSeqMSDFirst(maxn, r).length
    val fillDigits = maxNumDigits - digitSeq.length
    val padding = List.fill(fillDigits)(0)
    padding ++ digitSeq
  }

  /** Returns # of Base r digits needed to represent the number n */
  def numDigits(n: Int, r: Int): Int = toDigitSeqInternal(n, r).length
}