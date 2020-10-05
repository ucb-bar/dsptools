// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3.util.ShiftRegister
import chisel3.{Bool, Data, Mux}
import dsptools.hasContext

/**
  * Much of this is drawn from non/spire, but using Chisel Bools instead of
  * Java Bools. I suppose a more general solution would be generic in
  * return type, but the use cases there seem obscure.
  */

/**
  * A trait for things that have some notion of sign and the ability to ensure
  * something has a positive sign.
  */
trait Signed[A] extends Any {
  /** Returns Zero if `a` is 0, Positive if `a` is positive, and Negative is `a` is negative. */
  def sign(a: A): Sign = Sign(signum(a))

  /** Returns 0 if `a` is 0, > 0 if `a` is positive, and < 0 is `a` is negative. */
  def signum(a: A): ComparisonBundle

  /** An idempotent function that ensures an object has a non-negative sign. */
  def abs(a: A): A
  //noinspection ScalaStyle
  def context_abs(a: A): A

  def isSignZero(a: A): Bool = signum(a).eq
  def isSignPositive(a: A): Bool = !isSignZero(a) && !isSignNegative(a)
  def isSignNegative(a: A): Bool = signum(a).lt

  def isSignNonZero(a: A): Bool = !isSignZero(a)
  def isSignNonPositive(a: A): Bool = !isSignPositive(a)
  def isSignNonNegative(a: A): Bool = !isSignNegative(a)

}

object Signed {
  implicit def orderedRingIsSigned[A <: Data: Order: Ring]: Signed[A] = new OrderedRingIsSigned[A]

  def apply[A <: Data](implicit s: Signed[A]): Signed[A] = s
}

//scalastyle:off method.name
private class OrderedRingIsSigned[A <: Data](implicit o: Order[A], r: Ring[A]) extends Signed[A] with hasContext {
  def signum(a: A): ComparisonBundle = o.compare(a, r.zero)
  def abs(a: A): A = {
    Mux(signum(a).lt, r.negate(a), a)
  }
  def context_abs(a: A): A = {
    Mux(signum(ShiftRegister(a, context.numAddPipes)).lt, r.negateContext(a), ShiftRegister(a, context.numAddPipes))
  }
}

