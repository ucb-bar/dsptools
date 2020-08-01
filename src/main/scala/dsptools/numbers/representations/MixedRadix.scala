// See LICENSE for license details.

package dsptools.numbers.representations

import org.scalatest.{FlatSpec, Matchers}
import chisel3._
import generatortools.io.CustomBundle


object MixedRadix {
  /** Convert a mixed-radix digit sequence (least significant digits highested indexed)
    * to standard base 10 representation.
    */
  def toInt(digits: Seq[Int], radicesHighFirst: Seq[Int]): Int = {
    require(digits.length <= radicesHighFirst.length, "Seqs: digits must be shorter than radicesHighFirst")
    val muls = radicesHighFirst.tail.scanRight(1) { case (rad, prev) => prev * rad }
    val digitsPadded = Seq.fill(radicesHighFirst.length - digits.length)(0) ++ digits
    digitsPadded.zip(muls).map { case (a, b) => a * b }.sum
  }
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
  def toPaddedDigitSeqMSDFirst(n: Int, radicesHighFirst: Seq[Int]): Seq[Int] = {
    val len = radicesHighFirst.length
    val digitSeq = toDigitSeqMSDFirst(n, radicesHighFirst)
    Seq.fill(len - digitSeq.length)(0) ++ digitSeq
  }

  /** Zero pads Seq[Int] base-r_i representation */
  def toDigitSeqMSDFirst(n: Int, radicesHighFirst: Seq[Int], maxn: Int): Seq[Int] = {
    val digitSeq = toDigitSeqMSDFirst(n, radicesHighFirst)
    val maxNumDigits = toDigitSeqMSDFirst(maxn, radicesHighFirst).length
    val fillDigits = maxNumDigits - digitSeq.length
    val padding = Seq.fill(fillDigits)(0)
    padding ++ digitSeq
  }

  /** Returns # of Base r_i digits needed to represent the number n */
  def numDigits(n: Int, radicesHighFirst: Seq[Int]): Int =
    toDigitSeqInternal(n, radicesHighFirst.reverse).length

  /** a + b, where a and b are in mixed radix form. Optional carry in */
  def add(aShort: Seq[Int], bShort: Seq[Int], radicesHighFirst: Seq[Int], carryIn: Boolean = false): Seq[Int] = {
    val a = Seq.fill(radicesHighFirst.length - aShort.length)(0) ++ aShort
    val b = Seq.fill(radicesHighFirst.length - bShort.length)(0) ++ bShort
    // Out tuple (result digit, carry out)
    val carryInLSD = if (carryIn) 1 else 0
    val sum = a.last + b.last + carryInLSD
    val carryOutLSD = if (sum >= radicesHighFirst.last) 1 else 0
    val resultLSD = (sum) % radicesHighFirst.last
    val (result, carries) = a.init.zip(b.init).zip(radicesHighFirst.init).scanRight((resultLSD, carryOutLSD)) {
      case (((aDigit, bDigit), rad), (rightResult, rightCarryOut)) =>
        // Carry out is either 0 or 1
        // Sum guaranteed to be less than 2 * current radix so modulo operation can be
        // computed with simple mux circuit
        val sum = aDigit + bDigit + rightCarryOut
        (sum % rad, if (sum >= rad) 1 else 0)
    }.unzip
    result
  }

  /** Create a MixedRadix Chisel type with digit radices specified. Note that
    * the least significant digit is the highest indexed here. Radices can be
    * changed -- limited by original UInt bitwidths. Note that
    * technically, digits are capped at radix_i - 1, but not optimized here.
    */
  def apply(radicesHighFirst: Seq[UInt]): MixedRadix = {
    // Digits should have the same bitwidth as radices -- cloned internally.
    // TODO: actual range is rad - 1
    new MixedRadix(radicesHighFirst, radicesHighFirst)
  }
  /** Creates a MixedRadix wire and assigns to it */
  def wire(digits: Seq[UInt], radicesHighFirst: Seq[UInt]): MixedRadix = {
    val result = Wire(new MixedRadix(digits, radicesHighFirst))
    result.digits.seq.zip(digits) foreach { case (lhs, rhs) => lhs := rhs }
    result.radicesHighFirst.seq.zip(radicesHighFirst) foreach { case (lhs, rhs) => lhs := rhs }
    result
  }
  /** Creates a MixedRadix "Lit". Currently, you can only assign a MixedRadix to Lits.
    * TODO: Correct!
    */
  def apply(digits: Seq[Int], radicesHighFirst: Seq[Int]): MixedRadix = {
    val digitsProto = digits.map { case digit => digit.U }
    val radicesHighFirstProto = radicesHighFirst.map { case rad => rad.U }
    val result = Wire(new MixedRadix(digitsProto, radicesHighFirstProto))
    result.digits.seq.zip(digits) foreach { case (lhs, rhs) => lhs := rhs.U }
    result.radicesHighFirst.seq.zip(radicesHighFirst) foreach { case (lhs, rhs) => lhs := rhs.U }
    result
  }
}

class MixedRadix(digitsProto: Seq[UInt], radicesHighFirstProto: Seq[UInt]) extends Bundle {
  val digits = CustomBundle(digitsProto)
  val radicesHighFirst = CustomBundle(radicesHighFirstProto)
  /** Necessary Chisel helper for cloning this data type */
  override def cloneType = new MixedRadix(digits.seq, radicesHighFirst.seq).asInstanceOf[this.type]
  /** Add two mixed-radix numbers. carryIn set to true adds an extra 1.
    * WARNING -- no check to see if radices are the same (assumed to be true).
    */
  def add(b: MixedRadix, carryIn: Bool = false.B): MixedRadix = {
    require(this.digits.seq.length == b.digits.seq.length, "a, b lengths must be the same.")
    val aDigits = this.digits.seq
    val bDigits = b.digits.seq
    val rads = this.radicesHighFirst.seq
    // Since a digit value is always less than its associated radix,
    // resultTemp < 2 * radix
    val resultLSDTemp = aDigits.last + bDigits.last + carryIn.asUInt
    // Assume a, b radices are the same -- carry out is either 0 or 1
    val carryOutLSD = resultLSDTemp >= rads.last
    // Equivalent of resultTemp % radix since resultTemp < 2 * radix
    // Due to condition, modulo can be computed with a simple mux circuit
    val resultLSD = Mux(carryOutLSD, resultLSDTemp - rads.last, resultLSDTemp)
    val (result, carryOuts) = aDigits.init.zip(bDigits.init).zip(rads.init).scanRight((resultLSD, carryOutLSD.asUInt)) {
      case (((aDigit, bDigit), rad), (rightResult, rightCarryOut)) =>
        val resultTemp = aDigit + bDigit + rightCarryOut
        val carryOut = resultTemp >= rad
        val out = Mux(carryOut, resultTemp - rad, resultTemp)
        (out, carryOut.asUInt)
    }.unzip
    MixedRadix.wire(result, rads)
  }
}