// See LICENSE for license details.

package dsptools.numbers.representations

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