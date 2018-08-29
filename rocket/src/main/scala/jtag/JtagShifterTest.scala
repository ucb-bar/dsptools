// See LICENSE for license details.

package jtag.test

import org.scalatest._

import chisel3.iotesters.experimental.ImplicitPokeTester

import chisel3._
import jtag._
/*
trait ChainIOUtils extends ImplicitPokeTester {
  // CaptureUpdateChain test utilities
  def nop(io: ChainIO)(implicit t: InnerTester) {
    poke(io.chainIn.shift, 0)
    poke(io.chainIn.capture, 0)
    poke(io.chainIn.update, 0)

    check(io.chainOut.shift, 0)
    check(io.chainOut.capture, 0)
    check(io.chainOut.update, 0)
  }

  def capture(io: ChainIO)(implicit t: InnerTester) {
    poke(io.chainIn.shift, 0)
    poke(io.chainIn.capture, 1)
    poke(io.chainIn.update, 0)

    check(io.chainOut.shift, 0)
    check(io.chainOut.capture, 1)
    check(io.chainOut.update, 0)
  }

  def shift(io: ChainIO, expectedDataOut: BigInt, dataIn: BigInt)(implicit t: InnerTester) {
    poke(io.chainIn.shift, 1)
    poke(io.chainIn.capture, 0)
    poke(io.chainIn.update, 0)

    check(io.chainOut.shift, 1)
    check(io.chainOut.capture, 0)
    check(io.chainOut.update, 0)

    check(io.chainOut.data, expectedDataOut)
    poke(io.chainIn.data, dataIn)
  }

  def update(io: ChainIO)(implicit t: InnerTester) {
    poke(io.chainIn.shift, 0)
    poke(io.chainIn.capture, 0)
    poke(io.chainIn.update, 1)

    check(io.chainOut.shift, 0)
    check(io.chainOut.capture, 0)
    check(io.chainOut.update, 1)

  }

  // CaptureChain test utilities
  def nop(io: CaptureChain[Data]#ModIO)(implicit t: InnerTester) {
    nop(io.asInstanceOf[ChainIO])
    check(io.capture.capture, 0)
  }

  def capture(io: CaptureChain[Data]#ModIO)(implicit t: InnerTester) {
    capture(io.asInstanceOf[ChainIO])
    check(io.capture.capture, 1)
  }

  def shift(io: CaptureChain[Data]#ModIO, expectedDataOut: BigInt, dataIn: BigInt)(implicit t: InnerTester) {
    shift(io.asInstanceOf[ChainIO], expectedDataOut, dataIn)
    check(io.capture.capture, 0)
  }

  def update(io: CaptureChain[Data]#ModIO)(implicit t: InnerTester) {
    update(io.asInstanceOf[ChainIO])
    check(io.capture.capture, 0)
  }

  // CaptureUpdateChain test utilities
  def nop(io: CaptureUpdateChain[Data, Data]#ModIO)(implicit t: InnerTester) {
    nop(io.asInstanceOf[ChainIO])
    check(io.capture.capture, 0)
    check(io.update.valid, 0)
  }

  def capture(io: CaptureUpdateChain[Data, Data]#ModIO)(implicit t: InnerTester) {
    capture(io.asInstanceOf[ChainIO])
    check(io.capture.capture, 1)
    check(io.update.valid, 0)
  }

  def shift(io: CaptureUpdateChain[Data, Data]#ModIO, expectedDataOut: BigInt, dataIn: BigInt)(implicit t: InnerTester) {
    shift(io.asInstanceOf[ChainIO], expectedDataOut, dataIn)
    check(io.capture.capture, 0)
    check(io.update.valid, 0)
  }

  def update(io: CaptureUpdateChain[Data, Data]#ModIO)(implicit t: InnerTester) {
    update(io.asInstanceOf[ChainIO])
    check(io.capture.capture, 0)
    check(io.update.valid, 1)
  }
}
*/