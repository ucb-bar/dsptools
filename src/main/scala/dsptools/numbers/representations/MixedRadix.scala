package dsptools.numbers.representations
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

object MixedRadix {
  /** Converts a decimal representation of the number n into a Seq of
    * Ints representing the base-r_i interpretation of n
    * NOTE: Least significant digit is highest indexed (right-most) due to recursion
    * NOTE: Radix of least significant digit is 0-indexed in radicesLowFirst
    */
  private def toDigitSeqInternal(n: Int, radicesLowFirst: Seq[Int]): Seq[Int] = {
    require(n >= 0, "n must be >= 0")
    radicesLowFirst foreach { r =>
      require(r > 0, s"r $r must be > 0")
    }

    if (n != 0 && radicesLowFirst.isEmpty)
      throw new Exception("N is out of range for the given set of radices!")

    // Start dividing from LSD (resolves LSDs first)
    if (n == 0) Nil
    else toDigitSeqInternal(n / radicesLowFirst.head, radicesLowFirst.tail) :+ (n % radicesLowFirst.head)
  }

  /** Base-r_i representation of n, most significant digit 0-indexed (LSD highest-indexed i.e. right-most) */
  def toDigitSeqMSDFirst(n: Int, radicesHighFirst: Seq[Int]): Seq[Int] = {
    // Assume radix of LSD is high-indexed for radicesHighFirst
    // Or alternatively, 0-index of radicesHighFirst corresponds to MSD
    // But toDigitSeqInternal resolves LSDs first, so need to reverse
    val temp = toDigitSeqInternal(n, radicesHighFirst.reverse)
    // Should return non-empty list
    if (temp.isEmpty) Seq(0) else temp
  }
  /** Zero pads Seq[Int] base-r_i representation */
  def toDigitSeqMSDFirst(n: Int, radicesHighFirst: Seq[Int], maxn: Int): Seq[Int] = {
    val digitSeq = toDigitSeqMSDFirst(n, radicesHighFirst)
    val maxNumDigits = toDigitSeqMSDFirst(maxn, radicesHighFirst).length
    val fillDigits = maxNumDigits - digitSeq.length
    val padding = List.fill(fillDigits)(0)
    padding ++ digitSeq
  }

  /** Returns # of Base r_i digits needed to represent the number n */
  def numDigits(n: Int, radicesHighFirst: Seq[Int]): Int =
    toDigitSeqInternal(n, radicesHighFirst.reverse).length

}