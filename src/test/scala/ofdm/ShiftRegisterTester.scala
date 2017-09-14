package ofdm

import chisel3._
import chisel3.iotesters.PeekPokeTester

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

class ShiftRegisterTester(c: ShiftRegisterMem[UInt], depth: Int) extends PeekPokeTester(c) {
  val width: Int = c.gen.getWidth

  var validIns : ArrayBuffer[BigInt] = mutable.ArrayBuffer[BigInt]()
  var validOuts: ArrayBuffer[BigInt] = mutable.ArrayBuffer[BigInt]()

  // set depth
  poke(c.io.depth.valid, 1)
  poke(c.io.depth.bits, depth)
  poke(c.io.in.valid, 0)
  step(1)

  poke(c.io.depth.valid, 0)
  expect(c.io.out.valid, 0)

  for (i <- 0 until 100 * depth) {
    val in = BigInt(width, Random)
    val valid = Random.nextBoolean()

    if (valid) {
      validIns += in
    }
    poke(c.io.in.bits, in)
    poke(c.io.in.valid, valid)

    if (peek(c.io.out.valid) != BigInt(0)) {
      validOuts += peek(c.io.out.bits)
    }

    step(1)
  }

  // println(s"validIns = $validIns")
  // println(s"validOuts = ${validOuts.drop(depth)}")

  validOuts.drop(depth).zip(validIns).zipWithIndex.foreach { case ((out, in), idx) =>
      require(out == in, s"Output $idx incorrect: got $out, should be $in")
  }
}
