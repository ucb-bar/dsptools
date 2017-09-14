// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import chisel3.iotesters._
import dsptools.numbers._
import dsptools.numbers.implicits._
import org.scalatest.{FreeSpec, Matchers}

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
  gen, {in: T => Signed[T].abs(in) }
)

class FuncTester[T <: FuncModule[SInt]](dut: T, inputs: Seq[Int], outputs: Seq[Int])
  extends PeekPokeTester(dut) {
    inputs.zip(outputs).foreach { case(in, out) =>
      poke(dut.io.in, in)
      expect(dut.io.out, out)
    }
}

class TypeclassSpec extends FreeSpec with Matchers {
  "Ring[T].func() should work" in {
    dsptools.Driver.execute( () => new RingModule(SInt(10.W)) ) { c =>
      new FuncTester(c, Seq(2, -3), Seq(2, -3))
    } should be (true)
  }
  "Eq[T].func() should work" in {
    dsptools.Driver.execute( () => new EqModule(SInt(10.W)) ) { c =>
      new FuncTester(c, Seq(2, 0), Seq(2, 1))
    } should be (true)
  }
  "Integer[T].func() should work" in {
    dsptools.Driver.execute( () => new IntegerModule(SInt(10.W)) ) { c =>
      new FuncTester(c, Seq(2, -3), Seq(2, -3))
    } should be (true)
  }
  "Order[T].func() should work" in {
    dsptools.Driver.execute( () => new OrderModule(SInt(10.W)) ) { c =>
      new FuncTester(c, Seq(2, -3), Seq(2, -3))
    } should be (true)
  }
  "PartialOrder[T].func() should work" in {
    dsptools.Driver.execute( () => new PartialOrderModule(SInt(10.W)) ) { c =>
      new FuncTester(c, Seq(2, -3), Seq(2, -3))
    } should be (true)
  }
  "Signed[T].func() should work" in {
    dsptools.Driver.execute( () => new SignedModule(SInt(10.W)) ) { c =>
      new FuncTester(c, Seq(2, -3), Seq(2, 3))
    } should be (true)
  }
}
