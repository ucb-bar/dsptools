package ofdm

import breeze.numerics.{abs, cos, pow, sin}
import chisel3._
import chisel3.experimental.FixedPoint
import dsptools.numbers.implicits._
import org.scalatest.{FlatSpec, Matchers}

class CORDICSpec extends FlatSpec with Matchers {
  behavior of "Iterative CORDIC with fixed point"

  it should "convert rectangular to polar" in {
    chisel3.iotesters.Driver.execute(
      //Array("-tbn", "firrtl", "-fiwv"),
      Array("-tbn", "verilator", "--wave-form-file-name", "IterativeCORDIC.vcd"),
      () => IterativeCORDIC.circularVectoring(FixedPoint(16.W, 8.BP), FixedPoint(16.W, 12.BP))) { c =>
      new FixedPointIterativeCORDICTester(c) {
        val maxAngleIdxs = 500
        for (mag <- 0.5 to 1.5 by 0.2) {
          for (angleIdx <- -maxAngleIdxs until maxAngleIdxs) {
            val angle = 0.5 * math.Pi * angleIdx.toDouble / maxAngleIdxs

            val x = mag * cos(angle)
            val y = mag * sin(angle)

            val result = rect2PolarTrial(x, y)

            val magErr = abs(result._1 / 1.66 - mag)
            val angleErr = abs(result._2 - angle)

            val magThresh = pow(2.0, -5)
            val angleThresh = pow(2.0, -5)

            magErr should be < magThresh
            angleErr should be < angleThresh
          }
        }
      }
    }
  }

  it should "compute sine correctly" in {
    chisel3.iotesters.Driver.execute(
      //Array("-tbn", "firrtl", "-fiwv"),
      Array("-tbn", "verilator", "--wave-form-file-name", "IterativeCORDIC.vcd"),
      () => IterativeCORDIC.circularRotation(FixedPoint(16.W, 8.BP), FixedPoint(16.W, 12.BP))) { c=>
      new FixedPointIterativeCORDICTester(c) {
        //val result = rect2PolarTrial(0.5, 0.5)
        //step(3)
        //println(s"The result was $result")

        val max = 500
        for (i <- -max until max) {
          val x = 0.5 * math.Pi * i.toDouble / max
          val res = sinTrial(x)
          val expected = sin(x) * 1.66
          val absErr = abs(res - expected)
          val thresh = pow(2.0, -5)
          println(s"absErr for x=$x was $absErr (got $res expected $expected)")
          (absErr < thresh) should be (true)
        }
      }
    }
  }

  it should "divide correctly" in {
    chisel3.iotesters.Driver.execute(
      //Array("-tbn", "firrtl", "-fiwv"),
      Array("-tbn", "verilator", "--wave-form-file-name", "IterativeCORDIC.vcd"),
      () => IterativeCORDIC.division(FixedPoint(16.W, 8.BP), FixedPoint(32.W, 8.BP), nStages = Some(10))) { c=>
        new FixedPointIterativeCORDICTester(c) {
          //val result = rect2PolarTrial(0.5, 0.5)
          //step(3)
          //println(s"The result was $result")

          for {
            j <- 1 to 4
            i <- 1 to 6
          } {
            val div = trial(i.toDouble, j.toDouble, 0.0)//rect2PolarTrial(i.toDouble, j.toDouble)._2
            println(s"$j / $i = $div")
          }
        }
      }
  }

  behavior of "Iterative CORDIC with SInt"
  it should "divide correctly" in {
    chisel3.iotesters.Driver.execute(
      //Array("-tbn", "firrtl", "-fiwv"),
      Array("-tbn", "verilator", "--wave-form-file-name", "IterativeCORDIC.vcd", "-tiv"),
      () => IterativeCORDIC.division(SInt(16.W), SInt(16.W), nStages = Some(16))) { c=>
      new SIntIterativeCORDICTester(c) {
        //val result = rect2PolarTrial(0.5, 0.5)
        //step(3)
        //println(s"The result was $result")

        for {
          i <- 1 to 4
          j <- 1 to 6
        } {
          val div = trial((i * 1).toDouble, j.toDouble, 0.0)//rect2PolarTrial(i.toDouble, j.toDouble)._2
          println(s"$j / $i = $div")
        }
      }
    }
  }

  behavior of "Pipelined CORDIC with fixed point"


}
