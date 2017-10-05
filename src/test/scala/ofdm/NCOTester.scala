package ofdm

import breeze.math.Complex
import chisel3._
import chisel3.experimental.FixedPoint
import chisel3.iotesters.PeekPokeTester
import dspblocks.PeekPokePackers
import dsptools.DspTester
import dsptools.numbers.DspReal

trait NCOTester[T <: Data] { this: PeekPokeTester[NCOTable[T]] =>
  def pokePhase(in: BigInt): Unit
  def peekOut(): Complex

  def maxIdx = (1 << dut.io.phase.getWidth) - 1

  def sweepPhase(): Array[Complex] = {
    var idx = 0

    val outArray = new Array[Complex](maxIdx)

    while (idx < maxIdx) {
      pokePhase(idx)
      step(1)
      outArray(idx) = peekOut()
      idx += 1
    }

    outArray
  }
}

class FixedNCOTester(c: NCOTable[FixedPoint]) extends DspTester(c) with NCOTester[FixedPoint] {
  // override def maxIdx = (1 << dut.io.phase.binaryPoint.get) - 1
  def pokePhase(in: BigInt) = poke(c.io.phase, in)
  def peekOut() = Complex(peek(c.io.cosOut), peek(c.io.sinOut))
}

class DspRealNCOTester(c: NCOTable[DspReal], override val maxIdx: Int = 16384) extends DspTester(c) with NCOTester[DspReal] {
  def pokePhase(in: BigInt) = poke(c.io.phase, in.toDouble / maxIdx.toDouble)
  def peekOut() = Complex(peek(c.io.cosOut), peek(c.io.sinOut))
}