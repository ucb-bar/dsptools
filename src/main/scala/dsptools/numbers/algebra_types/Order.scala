// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3._
import chisel3.util.ValidIO

/**
  * Much of this is drawn from non/spire, but using Chisel Bools instead of
  * Java Bools. I suppose a more general solution would be generic in
  * return type, but the use cases there seem obscure.
  */

/**
  * The `Order` type class is used to define a total ordering on some type `A`.
  * An order is defined by a relation <=, which obeys the following laws:
  *
  * - either x <= y or y <= x (totality)
  * - if x <= y and y <= x, then x == y (antisymmetry)
  * - if x <= y and y <= z, then x <= z (transitivity)
  *
  * The truth table for compare is defined as follows:
  *
  * x <= y    x >= y      Int
  * true      true        = 0     (corresponds to x == y)
  * true      false       < 0     (corresponds to x < y)
  * false     true        > 0     (corresponds to x > y)
  *
  * By the totality law, x <= y and y <= x cannot be both false.
  */
trait Order[A <: Data] extends Any with PartialOrder[A] {
  self =>

  def partialCompare(x: A, y: A): ValidIO[ComparisonBundle] = {
    val c = compare(x, y)
    // Always valid
    ComparisonHelper(true.B, c.eq, c.lt)
  }

  override def eqv(x: A, y: A): Bool = compare(x, y).eq
  override def gt(x: A, y: A): Bool = {
    val c = compare(x, y)
    !(c.eq || c.lt)
  }
  override def lt(x: A, y: A): Bool = compare(x, y).lt
  override def gteqv(x: A, y: A): Bool = {
    val c = compare(x, y)
    c.eq || (!c.lt)
  }
  override def lteqv(x: A, y: A): Bool = {
    val c = compare(x, y)
    c.lt || c.eq
  }

  def min(x: A, y: A): A = Mux(lt(x, y), x, y)
  def max(x: A, y: A): A = Mux(gt(x, y), x, y)
  def compare(x: A, y: A): ComparisonBundle

  /**
    * Defines an order on `B` by mapping `B` to `A` using `f` and using `A`s
    * order to order `B`.
    */
  override def on[B <: Data](f: B => A): Order[B] = new MappedOrder(this)(f)

  /**
    * Defines an ordering on `A` where all arrows switch direction.
    */
  override def reverse: Order[A] = new ReversedOrder(this)
}

private[numbers] class MappedOrder[A <: Data, B <: Data](order: Order[B])(f: A => B) extends Order[A] {
  def compare(x: A, y: A): ComparisonBundle = order.compare(f(x), f(y))
}

private[numbers] class ReversedOrder[A <: Data](order: Order[A]) extends Order[A] {
  def compare(x: A, y: A): ComparisonBundle = order.compare(y, x)
}

object Order {
  @inline final def apply[A <: Data](implicit o: Order[A]): Order[A] = o

  def by[A <: Data, B <: Data](f: A => B)(implicit o: Order[B]): Order[A] = o.on(f)

  def from[A <: Data](f: (A, A) => ComparisonBundle): Order[A] = new Order[A] {
    def compare(x: A, y: A): ComparisonBundle = f(x, y)
  }
}

