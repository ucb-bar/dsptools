// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3._
import chisel3.experimental.FixedPoint
import chisel3.iotesters._
import dsptools._
import dsptools.numbers._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/*
 * These tests mostly exist to ensure that expressions of the form
 *   Typeclass[T].function()
 * compile. Issue #17 talks about how this wasn't working for some
 * typeclasses
 */


class FuncModule[T <: Data](gen: T, func: T=>T) extends Module {
  val io = IO(new Bundle {
    val in = Input(gen.cloneType)
    val out = Output(gen.cloneType)
  })
  io.out := func(io.in)
}

object RingFunc {
  def apply[T<:Data:Ring](in: T): T = {
    val zero = Ring[T].zero
    val one  = Ring[T].one
    Ring[T].times(one, Ring[T].plus(in, zero))
  }
}
class RingModule[T <: Data : Ring](gen: T) extends FuncModule(gen, {in: T => RingFunc(in)})

object EqFunc {
  def apply[T <: Data : Eq : Ring](in: T): T = Mux(Eq[T].eqv(in, Ring[T].zero), Ring[T].one, in)
}
class EqModule[T <: Data : Eq : Ring](gen: T) extends FuncModule(gen, {in: T => EqFunc(in)})

object IntegerFunc {
  def apply[T <: Data : Integer](in: T): T =
    Integer[T].round(in) + IsIntegral[T].mod(in, in)
}
class IntegerModule[T <: Data : Integer](gen: T) extends FuncModule(gen, {in: T => IntegerFunc(in)})

object OrderFunc {
  def apply[T <: Data : Order](in: T): T =
    Order[T].min(in, in)
}
class OrderModule[T <: Data : Order](gen: T) extends FuncModule(gen, {in: T => OrderFunc(in)})

object PartialOrderFunc {
  def apply[T <: Data : PartialOrder](in: T): T =
    Mux(PartialOrder[T].partialCompare(in, in).bits.eq, in, in)
}
class PartialOrderModule[T <: Data : PartialOrder : Ring](gen: T) extends FuncModule(
  gen, {in: T => PartialOrderFunc(in)})

class SignedModule[T <: Data : Signed](gen: T) extends FuncModule(
  gen, {in: T => Mux(Signed[T].sign(in).neg, Signed[T].abs(in), Mux(Signed[T].sign(in).zero, in, in)) }
)

class BinaryRepresentationModule[T <: Data : BinaryRepresentation](gen: T) extends FuncModule(
  gen, {in : T => (((in << 2) >> 1) << 3.U) >> 2.U}
)

trait FuncTester[T <: Data, V] {
  def dut: FuncModule[T]
  def testInputs: Seq[V]
  def testOutputs: Seq[V]

  def myPoke(port: T, value: V): Unit
  def myExpect(port: T, value: V): Unit

  testInputs.zip(testOutputs).foreach { case(in, out) =>
    myPoke(dut.io.in, in)
    myExpect(dut.io.out, out)
  }
}

class SIntFuncTester[T <: FuncModule[SInt]](dut: T, val testInputs: Seq[Int], val testOutputs: Seq[Int])
extends PeekPokeTester(dut) with FuncTester[SInt, Int] {
  def myPoke(port: SInt, value: Int)   = poke(port, value)
  def myExpect(port: SInt, value: Int) = expect(port, value)
}

class FixedPointFuncTester[T <: FuncModule[FixedPoint]](dut: T, val testInputs: Seq[Double], val testOutputs: Seq[Double])
extends DspTester(dut) with FuncTester[FixedPoint, Double] {
  def myPoke(port: FixedPoint, value: Double) = poke(port, value)
  def myExpect(port: FixedPoint, value: Double) = expect(port, value)
}

class DspRealFuncTester[T <: FuncModule[DspReal]](dut: T, val testInputs: Seq[Double], val testOutputs: Seq[Double])
extends DspTester(dut) with FuncTester[DspReal, Double] {
  def myPoke(port: DspReal, value: Double) = poke(port, value)
  def myExpect(port: DspReal, value: Double) = expect(port, value)
}

class TypeclassSpec extends AnyFreeSpec with Matchers {
  "Ring[T].func() should work" in {
    dsptools.Driver.execute( () => new RingModule(SInt(10.W)) ) { c =>
      new SIntFuncTester(c, Seq(2, -3), Seq(2, -3))
    } should be (true)
    dsptools.Driver.execute( () => new RingModule(FixedPoint(10.W, 4.BP)) ) { c =>
      new FixedPointFuncTester(c, Seq(2, -3), Seq(2, -3))
    } should be (true)
    dsptools.Driver.execute( () => new RingModule(DspReal()) ) { c =>
      new DspRealFuncTester(c, Seq(2, -3), Seq(2, -3))
    } should be (true)
  }
  "Eq[T].func() should work" in {
    dsptools.Driver.execute( () => new EqModule(SInt(10.W)) ) { c =>
      new SIntFuncTester(c, Seq(2, 0), Seq(2, 1))
    } should be (true)
    dsptools.Driver.execute( () => new EqModule(FixedPoint(10.W, 4.BP)) ) { c =>
      new FixedPointFuncTester(c, Seq(2, 0), Seq(2, 1))
    } should be (true)
    dsptools.Driver.execute( () => new EqModule(DspReal()) ) { c =>
      new DspRealFuncTester(c, Seq(2, 0), Seq(2, 1))
    } should be (true)
  }
  "Integer[T].func() should work" in {
    dsptools.Driver.execute( () => new IntegerModule(SInt(10.W)) ) { c =>
      new SIntFuncTester(c, Seq(2, -3), Seq(2, -3))
    } should be (true)
  }
  "Order[T].func() should work" in {
    dsptools.Driver.execute( () => new OrderModule(SInt(10.W)) ) { c =>
      new SIntFuncTester(c, Seq(2, -3), Seq(2, -3))
    } should be (true)
    dsptools.Driver.execute( () => new OrderModule(FixedPoint(10.W, 4.BP)) ) { c =>
      new FixedPointFuncTester(c, Seq(2, -3), Seq(2, -3))
    } should be (true)
    dsptools.Driver.execute( () => new OrderModule(DspReal()) ) { c =>
      new DspRealFuncTester(c, Seq(2, -3), Seq(2, -3))
    } should be (true)
  }
  "PartialOrder[T].func() should work" in {
    dsptools.Driver.execute( () => new PartialOrderModule(SInt(10.W)) ) { c =>
      new SIntFuncTester(c, Seq(2, -3), Seq(2, -3))
    } should be (true)
    dsptools.Driver.execute( () => new PartialOrderModule(FixedPoint(10.W, 4.BP)) ) { c =>
      new FixedPointFuncTester(c, Seq(2, -3), Seq(2, -3))
    } should be (true)
    dsptools.Driver.execute( () => new PartialOrderModule(DspReal()) ) { c =>
      new DspRealFuncTester(c, Seq(2, -3), Seq(2, -3))
    } should be (true)
  }
  "Signed[T].func() should work" in {
    dsptools.Driver.execute( () => new SignedModule(SInt(10.W)) ) { c =>
      new SIntFuncTester(c, Seq(2, -3), Seq(2, 3))
    } should be (true)
    dsptools.Driver.execute( () => new SignedModule(FixedPoint(10.W, 4.BP)) ) { c =>
      new FixedPointFuncTester(c, Seq(2, -3), Seq(2, 3))
    } should be (true)
    dsptools.Driver.execute( () => new SignedModule(DspReal()) ) { c =>
      new DspRealFuncTester(c, Seq(2, -3), Seq(2, 3))
    } should be (true)
  }
  "BinaryRepresentation[T].func() should work" in {
    dsptools.Driver.execute( () => new BinaryRepresentationModule(SInt(10.W)) ) { c =>
      new SIntFuncTester(c, Seq(2, 3), Seq(8, 12))
    } should be (true)
    dsptools.Driver.execute( () => new BinaryRepresentationModule(FixedPoint(10.W, 4.BP)) ) { c =>
      new FixedPointFuncTester(c, Seq(2, 3), Seq(8, 12))
    } should be (true)
    dsptools.Driver.execute( () => new BinaryRepresentationModule(DspReal()) ) { c =>
      new DspRealFuncTester(c, Seq(2, 3), Seq(8, 12))
    } should be (true)
  }
}
