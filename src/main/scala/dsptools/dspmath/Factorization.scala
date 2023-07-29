// SPDX-License-Identifier: Apache-2.0

package dsptools.dspmath

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
    val radPows = for (primeGroup <- supportedRads) yield {
      for (rad <- primeGroup) yield {
        var (mod, pow) = (0, 0)
        while (mod == 0) {
          mod = unfactorized % rad
          if (mod == 0) {
            pow = pow + 1
            unfactorized = unfactorized / rad
          }
        }
        RadPow(rad, pow)
      }
    }
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
