// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

object TrigUtility {

  // Sets how accurate things can be
  val numTaylorTerms = 8
  val atanM = 3
  // Double calcs have difficulty with small numbers
  val err = 1e6

  // Calculates Bernoulli numbers via the Akiyama–Tanigawa algorithm
  // @ https://en.wikipedia.org/wiki/Bernoulli_number
  def bernoulli(n: Int): Double = {
    this.synchronized {
      var temp: Array[Double] = Array.fill(n + 1)(0.0) 
      for (m <- 0 to n) {
        temp(m) = 1.toDouble / (m + 1)
        for (j <- m to 1 by -1) {
          temp(j - 1) = j * (temp(j - 1) - temp(j))
        }
      }
      // Bn
      temp(0) 
    }
  }

  def factorial(n: Int): Int = (1 to n).product

  def combination(n: Int, k: Int): Double = factorial(n).toDouble / factorial(k) / factorial(n - k) 

  // See Taylor series for trig functions @ https://en.wikipedia.org/wiki/Taylor_series
  def sinCoeff(nmax: Int): Seq[(Double, Double)] = {
    (0 to nmax) map { n => {
      val fact = factorial(2 * n + 1)
      val factOutOfBounds = fact / err
      // If you divide by too large of a number, things go crazy
      val scaleFactor = if (factOutOfBounds <= 1) 1.0 else fact.toDouble / err
      val denom = if (factOutOfBounds <= 1) fact else err
      (math.pow(-1, n) / denom, scaleFactor)
    } }
  }
  def cosCoeff(nmax: Int): Seq[Double] = {
    (0 to nmax).map(n => math.pow(-1, n) / factorial(2 * n))
  }
  def tanCoeff(nmax: Int): Seq[Double] = {
    (1 to nmax).map(n => bernoulli(2 * n) * math.pow(2, 2 * n) * (math.pow(2, 2 * n) - 1) * math.pow(-1, n - 1) / factorial(2 * n))
  }

  // Fast convergence of arctan (arcsin, arccos derived)
  // See: http://myweb.lmu.edu/hmedina/papers/reprintmonthly156-161-medina.pdf
  def a(j: Int, m: Int): Double = {
    // Is Even
    if (j % 2 == 0) {
      val i = j / 2
      def sumTerm(k : Int) = math.pow(-1, k) * combination(4 * m, 2 * k)
      math.pow(-1, i + 1) * ((i + 1) to (2 * m)).map(k => sumTerm(k)).sum
    } 
    else {
      val i = (j + 1) / 2
      def sumTerm(k : Int) = math.pow(-1, k) * combination(4 * m, 2 * k + 1)
      math.pow(-1, i + 1) * (i to (2 * m - 1)).map(k => sumTerm(k)).sum
    }
  }

  def atanCoeff1(m: Int): Seq[Double] = {
    (1 to (2 * m)).map(j => math.pow(-1, j + 1) / (2 * j - 1))
  }
  def atanCoeff2(m: Int): Seq[Double] = {
    (0 to (4 * m - 2)).map(j => a(j, m) / math.pow(-1, m + 1) / math.pow(4, m) / (4 * m + j + 1))
  }

}