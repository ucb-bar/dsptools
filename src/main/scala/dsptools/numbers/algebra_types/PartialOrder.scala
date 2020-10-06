// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3.{Bool, Data, Mux}
import chisel3.util.{Valid, ValidIO}

// Note: For type classing normal Chisel number data types like UInt, SInt, FixedPoint, etc.
// you should *not* have to rely on PartialOrder (all comparisons to the same type are legal)
// Therefore, in their "top-level" type classes, you should be overriding eqv, lteqv, lt, gteqv, gt

/**
  * Much of this is drawn from non/spire, but using Chisel Bools instead of
  * Java Bools. I suppose a more general solution would be generic in
  * return type, but the use cases there seem obscure.
  */

/**
  * The `PartialOrder` type class is used to define a partial ordering on some type `A`.
  *
  * A partial order is defined by a relation <=, which obeys the following laws:
  *
  * - x <= x (reflexivity)
  * - if x <= y and y <= x, then x === y (anti-symmetry)
  * - if x <= y and y <= z, then x <= z (transitivity)
  *
  * To compute both <= and >= at the same time, we use a Double number
  * to encode the result of the comparisons x <= y and x >= y.
  * The truth table is defined as follows:
  *
  * x <= y    x >= y      Double
  * true      true        = 0.0     (corresponds to x === y)
  * false     false       = NaN     (x and y cannot be compared)
  * true      false       = -1.0    (corresponds to x < y)
  * false     true        = 1.0     (corresponds to x > y)
  *
  */
trait PartialOrder[A <: Data] extends Any with Eq[A] {
  self =>
  /** Result of comparing `x` with `y`. Returns ValidIO[ComparisonBundle]
    *  with `valid` false if operands are not comparable. If operands are
    * comparable, `bits.lt` will be true if `x` < `y` and `bits.eq` will
    * be true if `x` = `y``
    */
  def partialCompare(x: A, y: A): ValidIO[ComparisonBundle]
  /** Result of comparing `x` with `y`. Returns None if operands
    * are not comparable. If operands are comparable, returns Some[Int]
    * where the Int sign is:
    * - negative iff `x < y`
    * - zero     iff `x == y`
    * - positive iff `x > y`
    */

  /** Returns Some(x) if x <= y, Some(y) if x > y, otherwise None. */
  def pmin(x: A, y: A): ValidIO[A] = {
    val c = partialCompare(x, y)
    val value = Mux(c.bits.lt, x, y)
    val ret = Valid(value)
    ret.valid := c.valid
    ret
  }

  /** Returns Some(x) if x >= y, Some(y) if x < y, otherwise None. */
  def pmax(x: A, y: A): ValidIO[A] = {
    val c = partialCompare(x, y)
    val value = Mux(!c.bits.lt, x, y)
    val ret = Valid(value)
    ret.valid := c.valid
    ret
  }

  // The following should be overriden in priority for performance
  def eqv(x: A, y: A): Bool = {
    val c = partialCompare(x, y)
    c.bits.eq && c.valid
  }
  def lteqv(x: A, y: A): Bool = {
    val c = partialCompare(x, y)
    (c.bits.lt || c.bits.eq) && c.valid
  }
  def lt(x: A, y: A): Bool = {
    val c = partialCompare(x, y)
    c.bits.lt && c.valid
  }

  def gteqv(x: A, y: A): Bool = lteqv(y, x)
  def gt(x: A, y: A): Bool = lt(y, x)

  /**
    * Defines a partial order on `B` by mapping `B` to `A` using `f` and using `A`s
    * order to order `B`.
    */
  override def on[B <: Data](f: B => A): PartialOrder[B] = new MappedPartialOrder(this)(f)

  /**
    * Defines a partial order on `A` where all arrows switch direction.
    */
  def reverse: PartialOrder[A] = new ReversedPartialOrder(this)
}

private[numbers] class MappedPartialOrder[A <: Data, B <: Data](partialOrder: PartialOrder[B])(f: A => B) extends PartialOrder[A] {
  def partialCompare(x: A, y: A): ValidIO[ComparisonBundle] = partialOrder.partialCompare(f(x), f(y))
}

private[numbers] class ReversedPartialOrder[A <: Data](partialOrder: PartialOrder[A]) extends PartialOrder[A] {
  def partialCompare(x: A, y: A): ValidIO[ComparisonBundle] = partialOrder.partialCompare(y, x)
}

object PartialOrder {
  @inline final def apply[A <: Data](implicit po: PartialOrder[A]): PartialOrder[A] = po

  def by[A <: Data, B <: Data](f: A => B)(implicit po: PartialOrder[B]): PartialOrder[A] = po.on(f)

  def from[A <: Data](f: (A, A) => ValidIO[ComparisonBundle]): PartialOrder[A] = new PartialOrder[A] {
    def partialCompare(x: A, y: A): ValidIO[ComparisonBundle] = f(x, y)
  }
}
