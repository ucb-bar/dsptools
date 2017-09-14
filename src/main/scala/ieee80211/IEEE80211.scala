package ieee80211

import breeze.linalg.DenseVector
import breeze.math.Complex
import breeze.numerics.sqrt
import breeze.signal.{iFourierShift, iFourierTr}
import co.theasi.plotly._

object IEEE80211 {
  val zero = Complex(0, 0)
  val pp   = Complex(1, 1)
  val pm   = Complex(1, -1)
  val mp   = Complex(-1, 1)
  val mm   = Complex(-1, -1)

  // indexed from bin -26 -> bin 26
  val stfFreq = DenseVector(
    zero, zero, zero, zero, mm,   zero, zero, zero,
    mm,   zero, zero, zero, pp,   zero, zero, zero,
    pp,   zero, zero, zero, pp,   zero, zero, zero,
    pp,   zero, zero, zero, zero, zero, zero, zero,
    zero, zero, zero, zero, zero, zero, zero, zero,
    pp,   zero, zero, zero, mm,   zero, zero, zero,
    pp,   zero, zero, zero, mm,   zero, zero, zero,
    mm,   zero, zero, zero, pp,   zero, zero, zero
  ) map { x => sqrt(13.0 / 6.0) * x }
  val stf64 = iFourierTr(stfFreq)

  val stf = DenseVector.vertcat(stf64, stf64, stf64).toArray.slice(0, 160)

  def main(args: Array[String]): Unit = {
    println(stf64)
    println(s"Length is ${stf64.size}")
    val real = stf64.map(_.real).toArray
    val imag = stf64.map(_.imag).toArray
    val xs = stf64.toArray.zipWithIndex.map(_._2)
    val plot = Plot().withScatter(xs, real).withScatter(xs, imag)
    draw(plot, "STF Time Domain")
  }
}
