// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3.{Data, Bool}

/**
  * Much of this is drawn from non/spire, but using Chisel Bools instead of
  * Java Bools. I suppose a more general solution would be generic in
  * return type, but the use cases there seem obscure.
  */

/**
  * A type class used to determine equality between 2 instances of the same
  * type. Any 2 instances `x` and `y` are equal if `eqv(x, y)` is `true`.
  * Moreover, `eqv` should form an equivalence relation.
  */
trait Eq[A <: Data] extends Any {
  /** Returns `true` if `x` and `y` are equivalent, `false` otherwise. */
  def eqv(x: A, y: A): Bool

  /** Returns `false` if `x` and `y` are equivalent, `true` otherwise. */
  def neqv(x: A, y:A ): Bool = !eqv(x, y)

  /**
    * Constructs a new `Eq` instance for type `B` where 2 elements are
    * equivalent iff `eqv(f(x), f(y))`.
    */
  def on[B <: Data](f: B => A): Eq[B] = new MappedEq(this)(f)
}

private[numbers] class MappedEq[A <: Data, B <: Data](eq: Eq[B])(f: A => B) extends Eq[A] {
  def eqv(x: A, y: A): Bool = eq.eqv(f(x), f(y))
}

object Eq {
  def apply[A <: Data](implicit e: Eq[A]): Eq[A] = e

  def by[A <: Data, B <: Data](f: A => B)(implicit e: Eq[B]): Eq[A] = new MappedEq(e)(f)
}





