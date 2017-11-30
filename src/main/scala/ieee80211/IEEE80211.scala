package ieee80211

import breeze.linalg.DenseVector
import breeze.math.Complex
import breeze.numerics.{cos, sin, sqrt}
import breeze.signal.{iFourierShift, iFourierTr}
import co.theasi.plotly._

object IEEE80211 {
  val zero = Complex(0, 0)
  val one  = Complex(1, 0)
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

  val ltfFreq = DenseVector(
    zero,
    one, -one, -one,  one,  one, -one,  one, -one,
    one, -one, -one, -one, -one, -one, one,  one,
    -one, -one,  one, -one,  one, -one,  one,  one,
    one,  one,

    zero, zero, zero, zero, zero,
    zero, zero, zero, zero, zero, zero,

    one, one,
    -one, -one, one, one, -one, one, -one, one,
    one, one, one, one, one, -one, -one, one,
    one, -one, one, -one, one, one, one, one

  )

  val ltf64 = iFourierTr(ltfFreq)

  val ltf = DenseVector.vertcat(ltf64, ltf64, ltf64).toArray.slice(32,160+32)
  

  def addCFO(in: Seq[Complex], cfo: Double = 0.0, sampleRate: Double = 20.0e6): Seq[Complex] = {
    val coeff = 2 * math.Pi * cfo / sampleRate
    in.zipWithIndex.map { case (samp, idx) =>
      val rotation = Complex(cos(coeff * idx), sin(coeff * idx))
      rotation * samp
    }
  }

  def main(args: Array[String]): Unit = {
    println(stf64)
    println(s"Length is ${stf64.size}")
    /*val real = stf64.map(_.real).toArray
    val imag = stf64.map(_.imag).toArray
    val xs = stf64.toArray.zipWithIndex.map(_._2)
    val plot = Plot().withScatter(xs, real).withScatter(xs, imag)
    draw(plot, "STF Time Domain")*/

    println(s"LTF is $ltf64")
    /*val ltfReal = ltf64.map(_.real).toArray
    val ltfImag = ltf64.map(_.imag).toArray
    val ltfXs = 1 to 64
    val ltfPlot = Plot().withScatter(ltfXs, ltfReal).withScatter(ltfXs, ltfImag)
    draw(ltfPlot, "LTF Time Domain")*/
  }
}
