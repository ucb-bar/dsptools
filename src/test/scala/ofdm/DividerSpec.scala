package ofdm

import chisel3.iotesters.PeekPokeTester
import org.scalatest.{FlatSpec, Matchers}

class PipelinedDividerTester(c: PipelinedDivider) extends PeekPokeTester(c) {
  def trialsBigInt(in: Seq[(BigInt, BigInt)]): Seq[BigInt] = {
    var out: Seq[BigInt] = Seq()
    poke(c.io.in.valid, 1)
    for ((n, d) <- in) {
      poke(c.io.in.bits.num, n)
      poke(c.io.in.bits.denom, d)
      step(1)
      if (peek(c.io.out.valid) != BigInt(0)) {
        out :+= peek(c.io.out.bits)
      }
    }
    poke(c.io.in.valid, 0)
    // keep going to flush pipe
    for (i <- 0 until 4 * c.n) {
      step(1)
      if (peek(c.io.out.valid) != BigInt(0)) {
        out :+= peek(c.io.out.bits)
      }
    }
    require(out.length == in.length)
    out
  }
  def trials(in: Seq[(Int, Int)]): Seq[Int] = {
    trialsBigInt(in.map { case (n, d) => (BigInt(n), BigInt(d))}).map(_.toInt)
  }
}

class DividerSpec extends FlatSpec with Matchers {
  behavior of "Pipelined Divider"

  it should "divide all pairs of 8 bit numbers" in {
    chisel3.iotesters.Driver.execute(Array("-tbn", "verilator", "-fiwv"), () => new PipelinedDivider(8)) {
      c => new PipelinedDividerTester(c) {
        // trials(Seq( (12, 3), (12, 4), (12, 6), (1, 1), (2,1), (3,1), (0, 1) )).foreach(x => println(BigInt(x).toString(16)))


        val nums = Seq.tabulate(256) {x => x}.tail
        val pairs:Seq[(Int, Int)] = for {
          i <- Seq(0) ++ nums
          j <- nums
        } yield (i, j)

        //println(pairs.length.toString)

        val results = trials(pairs)
        //println(pairs.zip(results).toString)
        pairs.zip(results).foreach { case ((n, d), q) =>
          // println(s"$n / $d = $q")
          q should be (n / d)
        }
      }
    }
  }
}
