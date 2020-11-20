// SPDX-License-Identifier: Apache-2.0

package dsptools.dspmath

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

case class RadPow(rad: Int, pow: Int) {
  /** `r ^ p` */
  def get: Int = BigInt(rad).pow(pow).toInt
  /** Factorize i.e. rad = 4, pow = 3 -> Seq(4, 4, 4) */
  def factorize: Seq[Int] = Seq.fill(pow)(rad)
}

case class Factorization(supportedRadsUnsorted: Seq[Seq[Int]]) {
  /** Supported radices, MSD First */
  private val supportedRads = supportedRadsUnsorted.map(_.sorted.reverse)

  /** Factor n into powers of supported radices and store RadPow i.e. r^p, separated by coprimes
    * i.e. if supportedRads = Seq(Seq(4, 2), Seq(3)),
    * output =  Seq(Seq(RadPow(4, 5), RadPow(2, 1)), Seq(RadPow(3, 7)))
    * implies n = 4^5 * 2^1 * 3^7
    */
  private def getRadPows(n: Int): Seq[Seq[RadPow]] = {
    // Test if n can be factored by each of the supported radices (mod = 0)
    // Count # of times it can be factored
    var unfactorized = n
    val radPows = for (primeGroup <- supportedRads) yield { for (rad <- primeGroup) yield {
      var (mod, pow) = (0, 0)
      while (mod == 0) {
        mod = unfactorized % rad
        if (mod == 0) {
          pow = pow + 1
          unfactorized = unfactorized / rad
        }
      }
      RadPow(rad, pow)
    }}
    // If n hasn't completely been factorized, then an unsupported radix is required
    require(unfactorized == 1, s"$n is invalid for supportedRads.")
    radPows
  }

  /** Factor n into powers of supported radices (flattened)
    * i.e. if supportedRads = Seq(Seq(4, 2), Seq(3)),
    * output = Seq(5, 1, 7)
    * implies `n = 4^5 * 2^1 * 3^7`
    * If supportedRads contains more radices than the ones used, a power of 0 will be
    * associated with the unused radices.
    */
  def getPowsFlat(n: Int): Seq[Int] = {
    getRadPows(n).flatMap(_.map(_.pow))
  }

  /** Break n into coprimes i.e.
    * n = 4^5 * 2^1 * 3^7
    * would result in Seq(4^5 * 2^1, 3^7)
    * If supportedRads contains more coprime groups than the ones used, 1 will be
    * associated with the unused groups.
    */
  def getCoprimes(n: Int): Seq[Int] = {
    getRadPows(n).map(_.map(_.get).product)
  }

  /** Factorizes the coprime into digit radices (mixed radix)
    * i.e. n = 8 -> Seq(4, 2)
    * Note: there's no padding!
    */
  def factorizeCoprime(n: Int): Seq[Int] = {
    // i.e. if supportedRads = Seq(Seq(4, 2), Seq(3)) and n = 8,
    // correspondingPrimeGroup = Seq(4, 2)
    val correspondingPrimeGroup = supportedRads.filter(n % _.min == 0)
    require(correspondingPrimeGroup.length == 1, "n (coprime) must not be divisible by other primes.")
    // Factorize coprime -- only correspondingPrimeGroup should actually add to factorization length
    getRadPows(n).flatten.flatMap(_.factorize)
  }

  /** Gets associated base prime for n (assuming n isn't divisible by other primes)
    * WARNING: Assumes supportedRads contains the base prime!
    */
  def getBasePrime(n: Int): Int = {
    val primeTemp = supportedRads.map(_.min).filter(n % _ == 0)
    require(primeTemp.length == 1, "n should only be divisible by 1 prime")
    primeTemp.head
  }

}

class FactorizationSpec extends AnyFlatSpec with Matchers {

  val testSupportedRads = Seq(Seq(4, 2), Seq(3), Seq(5), Seq(7))

  behavior of "Factorization"
  it should "properly factorize" in {
    case class FactorizationTest(n: Int, pows: Seq[Int], coprimes: Seq[Int])
    val tests = Seq(
      FactorizationTest(
        n = (math.pow(4, 5) * math.pow(2, 1) * math.pow(3, 7)).toInt,
        pows = Seq(5, 1, 7),
        coprimes = Seq((math.pow(4, 5) * math.pow(2, 1)).toInt, math.pow(3, 7).toInt)
      ),
      FactorizationTest(n = 15, pows = Seq(0, 0, 1, 1), coprimes = Seq(1, 3, 5))
    )

    tests foreach { case FactorizationTest(n, pows, coprimes) =>
      val powsFill = Seq.fill(testSupportedRads.flatten.length - pows.length)(0)
      val coprimesFill = Seq.fill(testSupportedRads.length - coprimes.length)(1)
      require(
        Factorization(testSupportedRads).getPowsFlat(n) == pows ++ powsFill,
        "Should factorize to get the right powers -- includes padding."
      )
      require(
        Factorization(testSupportedRads).getCoprimes(n) == coprimes ++ coprimesFill,
        "Should factorize into the right coprimes -- includes padding."
      )
    }
  }

  it should "properly factorize coprime" in {
    case class CoprimeFactorizationTest(n: Int, factorization: Seq[Int], basePrime: Int)
    val tests = Seq(
      CoprimeFactorizationTest(n = 8, factorization = Seq(4, 2), basePrime = 2),
      CoprimeFactorizationTest(n = 16, factorization = Seq(4, 4), basePrime = 2)
    )
    tests foreach { case CoprimeFactorizationTest(n, factorization, basePrime) =>
      require(
        Factorization(testSupportedRads).factorizeCoprime(n) == factorization,
        "Should factorize coprime properly."
      )
      require(
        Factorization(testSupportedRads).getBasePrime(n) == basePrime,
        "Should get the correct base prime."
      )
    }
  }
}
