package ofdm

import chisel3._
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
      peekX()
      peekY()
      peekZ()
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

class UIntIterativeCORDICTester(val c: IterativeCORDIC[UInt]) extends PeekPokeTester(c)
  with IterativeCORDICTester[UInt] {

  def pokeX(in: Double): Unit = poke(c.io.in.bits.x, in.toInt)
  def pokeY(in: Double): Unit = poke(c.io.in.bits.y, in.toInt)
  def pokeZ(in: Double): Unit = poke(c.io.in.bits.z, in.toInt)
  def peekX(): Double = peek(c.io.out.bits.x).toDouble
  def peekY(): Double = peek(c.io.out.bits.y).toDouble
  def peekZ(): Double = peek(c.io.out.bits.z).toDouble
}
class SIntIterativeCORDICTester(val c: IterativeCORDIC[SInt]) extends PeekPokeTester(c)
  with IterativeCORDICTester[SInt] {

  def pokeX(in: Double): Unit = poke(c.io.in.bits.x, in.toInt)
  def pokeY(in: Double): Unit = poke(c.io.in.bits.y, in.toInt)
  def pokeZ(in: Double): Unit = poke(c.io.in.bits.z, in.toInt)
  def peekX(): Double = peek(c.io.out.bits.x).toDouble
  def peekY(): Double = peek(c.io.out.bits.y).toDouble
  def peekZ(): Double = peek(c.io.out.bits.z).toDouble
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

trait PipelinedCORDICTester[T <: Data] { this: PeekPokeTester[PipelinedCORDIC[T]] =>
  def c: PipelinedCORDIC[T]

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

  def trials(in: Seq[XYZ]): Seq[XYZ] = {
    var output: Seq[XYZ] = Seq()
    poke(c.io.in.valid, 1)
    for (xyz <- in) {
      implicit val _xyz = xyz
      pokeX(x)
      pokeY(y)
      pokeZ(z)
      step(1)
      if (peek(c.io.out.valid) != BigInt(0)) {
        output +:= (peekX, peekY, peekZ)
      }
    }
    // flush pipe
    var cycle = 0
    while (cycle < maxCycles && peek(c.io.out.valid) != BigInt(0)) {
      output +:= (peekX, peekY, peekZ)
      cycle += 1
    }

    output
  }

  def atan2Trials(in: Seq[(Double, Double)]): Seq[Double] = {
    trials(in.map { case (x, y) => (x, y, 0.0) }).map(_._3)
  }

  def atan2Trial(x: Double, y: Double): Double = {
    val out = atan2Trials(Seq((x, y)))
    require(out.length == 1)
    out(0)
  }

  def rotationTrials(in: Seq[XYZ]): Seq[XYZ] = {
    trials(in)
  }

  def rotationTrial(xyz: XYZ): XYZ = {
    val out = rotationTrials(Seq(xyz))
    require(out.length == 1)
    out(0)
  }

  def sinTrials(x: Seq[Double]): Seq[Double] = {
    rotationTrials(x.map( (1.0, 0.0, _))).map(_._2)
  }

  def sinTrial(x: Double): Double = {
    rotationTrial((1.0, 0.0, x))._2
  }

  def cosTrials(x: Seq[Double]): Seq[Double] = {
    rotationTrials(x.map( (1.0, 0.0, _))).map(_._1)
  }

  def cosTrial(x: Double): Double = {
    rotationTrial((1.0, 0.0, x))._1
  }

  def vecMagTrials(in: Seq[(Double, Double)]): Seq[Double] = {
    trials(in.map(i => (i._1, i._2, 0.0))).map(_._1)
  }

  def vecMagTrial(x: Double, y: Double): Double = {
    val out = vecMagTrials(Seq((x, y)))
    require(out.length == 1)
    out(0)
  }

  def polar2RectTrials(in: Seq[(Double, Double)]): Seq[(Double, Double)] = {
    trials(in.map(i => (i._1, 0.0, i._2))).map(o => (o._1, o._2))
  }

  def polar2RectTrial(r: Double, theta: Double): (Double, Double) = {
    val out = polar2RectTrials(Seq((r, theta)))
    require(out.length == 1)
    out(0)
  }

  def rect2PolarTrials(in: Seq[(Double, Double)]): Seq[(Double, Double)] = {
    trials(in.map(i => (i._1, i._2, 0.0))).map(o => (o._1, o._3))
  }

  def rect2PolarTrial(x: Double, y: Double): (Double, Double) = {
    val out = rect2PolarTrials(Seq((x, y)))
    require(out.length == 1)
    out(0)
  }
}


class FixedPointPipelinedCORDICTester(val c: PipelinedCORDIC[FixedPoint]) extends DspTester(c)
  with PipelinedCORDICTester[FixedPoint] {
  def pokeX(in: Double): Unit = poke(c.io.in.bits.x, in)
  def pokeY(in: Double): Unit = poke(c.io.in.bits.y, in)
  def pokeZ(in: Double): Unit = poke(c.io.in.bits.z, in)
  def peekX(): Double = peek(c.io.out.bits.x)
  def peekY(): Double = peek(c.io.out.bits.y)
  def peekZ(): Double = peek(c.io.out.bits.z)
}

class DspRealPipelinedCORDICTester(val c: PipelinedCORDIC[DspReal]) extends DspTester(c)
  with PipelinedCORDICTester[DspReal] {
  def pokeX(in: Double): Unit = poke(c.io.in.bits.x, in)
  def pokeY(in: Double): Unit = poke(c.io.in.bits.y, in)
  def pokeZ(in: Double): Unit = poke(c.io.in.bits.z, in)
  def peekX(): Double = peek(c.io.out.bits.x)
  def peekY(): Double = peek(c.io.out.bits.y)
  def peekZ(): Double = peek(c.io.out.bits.z)
}