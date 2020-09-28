// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3._
import spire.macros.Ops

import scala.language.experimental.macros

import chisel3.experimental.FixedPoint

/**
  * Much of this is drawn from non/spire, but using Chisel Bools instead of
  * Java Bools. I suppose a more general solution would be generic in
  * return type, but the use cases there seem obscure.
  */

final class EqOps[A <: Data](lhs: A)(implicit ev: Eq[A]) {
  def ===(rhs: A): Bool = macro Ops.binop[A, Bool]
  def =!=(rhs: A): Bool = macro Ops.binop[A, Bool]
  // Consistency with Chisel
  def =/=(rhs: A): Bool = ev.eqv(lhs, rhs)
}

final class PartialOrderOps[A <: Data](lhs: A)(implicit ev: PartialOrder[A]) {
  def >(rhs: A): Bool = macro Ops.binop[A, Bool]
  def >=(rhs: A): Bool = macro Ops.binop[A, Bool]
  def <(rhs: A): Bool = macro Ops.binop[A, Bool]
  def <=(rhs: A): Bool = macro Ops.binop[A, Bool]

  def partialCompare(rhs: A): Double = macro Ops.binop[A, Double]
  def tryCompare(rhs: A): Option[Int] = macro Ops.binop[A, Option[Int]]
  def pmin(rhs: A): Option[A] = macro Ops.binop[A, A]
  def pmax(rhs: A): Option[A] = macro Ops.binop[A, A]

  def >(rhs: Int)(implicit ev1: Ring[A]): Bool = macro Ops.binopWithLift[Int, Ring[A], A]
  def >=(rhs: Int)(implicit ev1: Ring[A]): Bool = macro Ops.binopWithLift[Int, Ring[A], A]
  def <(rhs: Int)(implicit ev1: Ring[A]): Bool = macro Ops.binopWithLift[Int, Ring[A], A]
  def <=(rhs: Int)(implicit ev1: Ring[A]): Bool = macro Ops.binopWithLift[Int, Ring[A], A]

  def >(rhs: Double)(implicit ev1: Field[A]): Bool = macro Ops.binopWithLift[Int, Field[A], A]
  def >=(rhs: Double)(implicit ev1: Field[A]): Bool = macro Ops.binopWithLift[Int, Field[A], A]
  def <(rhs: Double)(implicit ev1: Field[A]): Bool = macro Ops.binopWithLift[Int, Field[A], A]
  def <=(rhs: Double)(implicit ev1: Field[A]): Bool = macro Ops.binopWithLift[Int, Field[A], A]

  def >(rhs: spire.math.Number)(implicit c: ConvertableFrom[A]): Bool = (c.toNumber(lhs) > rhs).B
  def >=(rhs: spire.math.Number)(implicit c: ConvertableFrom[A]): Bool = (c.toNumber(lhs) >= rhs).B
  def <(rhs: spire.math.Number)(implicit c: ConvertableFrom[A]): Bool = (c.toNumber(lhs) < rhs).B
  def <=(rhs: spire.math.Number)(implicit c: ConvertableFrom[A]): Bool = (c.toNumber(lhs) <= rhs).B
}

final class OrderOps[A <: Data](lhs: A)(implicit ev: Order[A]) {
  def compare(rhs: A): ComparisonBundle = macro Ops.binop[A, ComparisonBundle]
  def min(rhs: A): A = macro Ops.binop[A, A]
  def max(rhs: A): A = macro Ops.binop[A, A]

  def compare(rhs: Int)(implicit ev1: Ring[A]): Int = macro Ops.binopWithLift[Int, Ring[A], A]
  def min(rhs: Int)(implicit ev1: Ring[A]): A = macro Ops.binopWithLift[Int, Ring[A], A]
  def max(rhs: Int)(implicit ev1: Ring[A]): A = macro Ops.binopWithLift[Int, Ring[A], A]

  def compare(rhs: Double)(implicit ev1: Field[A]): Int = macro Ops.binopWithLift[Int, Field[A], A]
  def min(rhs: Double)(implicit ev1: Field[A]): A = macro Ops.binopWithLift[Int, Field[A], A]
  def max(rhs: Double)(implicit ev1: Field[A]): A = macro Ops.binopWithLift[Int, Field[A], A]

  def compare(rhs: spire.math.Number)(implicit c: ConvertableFrom[A]): Int = c.toNumber(lhs) compare rhs
  def min(rhs: spire.math.Number)(implicit c: ConvertableFrom[A]): Number = c.toNumber(lhs) min rhs
  def max(rhs: spire.math.Number)(implicit c: ConvertableFrom[A]): Number = c.toNumber(lhs) max rhs
}

final class SignedOps[A: Signed](lhs: A) {
  def abs(): A = macro Ops.unop[A]
  def context_abs(): A = macro Ops.unop[A]
  def sign(): Sign = macro Ops.unop[Sign]
  def signum(): Int = macro Ops.unop[Int]

  def isSignZero(): Bool = macro Ops.unop[Bool]
  def isSignPositive(): Bool = macro Ops.unop[Bool]
  def isSignNegative(): Bool = macro Ops.unop[Bool]

  def isSignNonZero(): Bool = macro Ops.unop[Bool]
  def isSignNonPositive(): Bool = macro Ops.unop[Bool]
  def isSignNonNegative(): Bool = macro Ops.unop[Bool]
}

final class IsRealOps[A <: Data](lhs: A)(implicit ev: IsReal[A]) {
  def isWhole(): Bool = macro Ops.unop[Bool]
  def ceil(): A = macro Ops.unop[A]
  def context_ceil(): A = macro Ops.unop[A]
  def floor(): A = macro Ops.unop[A]
  def round(): A = macro Ops.unop[A]
  def truncate(): A = ev.truncate(lhs)
}

class IsIntegerOps[A <: Data](lhs: A)(implicit ev: IsIntegral[A]) {
  def mod(rhs: A): A = ev.mod(lhs, rhs)
  def %(rhs: A): A = mod(rhs)
  def isOdd(): Bool = ev.isOdd(lhs) 
  def isEven(): Bool = ev.isEven(lhs)
}

class ConvertableToOps[A <: Data](lhs: A)(implicit ev: ConvertableTo[A]) {
  def fromInt(i: Int): A = fromDouble(i.toDouble)
  def fromIntWithFixedWidth(i: Int): A = fromDoubleWithFixedWidth(i.toDouble)
  def fromDouble(d: Double): A = ev.fromDouble(d, lhs)
  def fromDoubleWithFixedWidth(d: Double): A = ev.fromDoubleWithFixedWidth(d, lhs)
}

class ChiselConvertableFromOps[A <: Data](lhs: A)(implicit ev: ChiselConvertableFrom[A]) {
  def intPart(): SInt = ev.intPart(lhs)
  def asFixed(): FixedPoint = ev.asFixed(lhs)
  def asFixed(proto: FixedPoint): FixedPoint = ev.asFixed(lhs, proto)
  def asReal(): DspReal = ev.asReal(lhs)
}

class BinaryRepresentationOps[A <: Data](lhs: A)(implicit ev: BinaryRepresentation[A]) {
  def <<(n: Int): A = ev.shl(lhs, n)
  def <<(n: UInt): A = ev.shl(lhs, n)
  def >>(n: Int): A = ev.shr(lhs, n)
  def >>(n: UInt): A = ev.shr(lhs, n)
  def signBit(): Bool = ev.signBit(lhs)
  def div2(n: Int): A = ev.div2(lhs, n)
  def mul2(n: Int): A = ev.mul2(lhs, n)
  def trimBinary(n: Int): A = ev.trimBinary(lhs, n)
}

class ContextualRingOps[A <: Data](lhs: A)(implicit ev: Ring[A]) {
  def context_+(rhs: A): A = ev.plusContext(lhs, rhs)
  def context_-(rhs: A): A = ev.minusContext(lhs, rhs)
  def context_*(rhs: A): A = ev.timesContext(lhs, rhs)
  def context_unary_-(): A = ev.negateContext(lhs)
}
