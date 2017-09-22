package ofdm

import chisel3.Data
import chisel3.experimental.FixedPoint
import chisel3.iotesters.PeekPokeTester
import dsptools.DspTester
import dsptools.numbers.DspReal

trait IterativeCORDICTester[T <: Data] { this: PeekPokeTester[IterativeCORDIC[T]] =>
  def c: IterativeCORDIC[T]

  val maxCycles = 100

  type XYZ = Tuple3[Double, Double, Double]

  def x(implicit xyz: XYZ): Double = xyz._1
  def y(implicit xyz: XYZ): Double = xyz._2
  def z(implicit xyz: XYZ): Double = xyz._3

  def pokeX(in: Double): Unit
  def pokeY(in: Double): Unit
  def pokeZ(in: Double): Unit

  def peekX(): Double
  def peekY(): Double
  def peekZ(): Double


  def trial(implicit in: XYZ): XYZ = {
    poke(c.io.in.valid, 1)
    poke(c.io.out.ready, 0)
    pokeX(x)
    pokeY(y)
    pokeZ(z)
    var inputTaken = false
    var cycles = 0
    while (!inputTaken && cycles < maxCycles) {
      inputTaken = peek(c.io.in.ready) != BigInt(0)
      cycles += 1
      step(1)
    }
    poke(c.io.in.valid, 0)
    poke(c.io.out.ready, 1)

    var outputDone = false
    cycles = 0
    while (!outputDone && cycles < maxCycles) {
      cycles += 1
      outputDone = peek(c.io.out.valid) != BigInt(0)
      step(1)
    }
    (peekX, peekY, peekZ)
  }

  def atan2Trial(x: Double, y: Double): Double = {
    trial((x, y, 0.0))._3
  }

  def rotationTrial(xyz: XYZ): XYZ = {
    trial(xyz)
  }

  def sinTrial(x: Double): Double = {
    rotationTrial((1.0, 0.0, x))._2
  }

  def cosTrial(x: Double): Double = {
    rotationTrial((1.0, 0.0, x))._1
  }

  def vecMagTrial(x: Double, y: Double): Double = {
    trial((x, y, 0.0))._1
  }

  def polar2RectTrial(r: Double, theta: Double): (Double, Double) = {
    val res = trial((r, 0.0, theta))
    (res._1, res._2)
  }

  def rect2PolarTrial(x: Double, y: Double): (Double, Double) = {
    val res = trial(x, y, 0.0)
    (res._1, res._3)
  }
}

class FixedPointIterativeCORDICTester(val c: IterativeCORDIC[FixedPoint]) extends DspTester(c)
  with IterativeCORDICTester[FixedPoint] {
  def pokeX(in: Double): Unit = poke(c.io.in.bits.x, in)
  def pokeY(in: Double): Unit = poke(c.io.in.bits.y, in)
  def pokeZ(in: Double): Unit = poke(c.io.in.bits.z, in)
  def peekX(): Double = peek(c.io.out.bits.x)
  def peekY(): Double = peek(c.io.out.bits.y)
  def peekZ(): Double = peek(c.io.out.bits.z)
}

class DspRealIterativeCORDICTester(val c: IterativeCORDIC[DspReal]) extends DspTester(c)
  with IterativeCORDICTester[DspReal] {
  def pokeX(in: Double): Unit = poke(c.io.in.bits.x, in)
  def pokeY(in: Double): Unit = poke(c.io.in.bits.y, in)
  def pokeZ(in: Double): Unit = poke(c.io.in.bits.z, in)
  def peekX(): Double = peek(c.io.out.bits.x)
  def peekY(): Double = peek(c.io.out.bits.y)
  def peekZ(): Double = peek(c.io.out.bits.z)
}
