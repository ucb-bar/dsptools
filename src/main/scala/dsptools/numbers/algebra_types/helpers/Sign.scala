// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3._
import chisel3.util.ShiftRegister
import dsptools.hasContext

import scala.language.implicitConversions

/**
  * Much of this is drawn from non/spire, but using Chisel Bools instead of
  * Java Bools. I suppose a more general solution would be generic in
  * return type, but the use cases there seem obscure.
  */

/**
  * A simple ADT representing the `Sign` of an object.
  */
sealed class Sign(zeroInit: Option[Boolean] = None, negInit: Option[Boolean] = None) extends Bundle {
  // import Sign._
  val zero = zeroInit.map{_.B}.getOrElse(Bool())
  // ignore neg if zero is true
  val neg  = negInit.map{_.B}.getOrElse(Bool())

  def unary_-(): Sign = Sign(this.zero, !this.neg)

  def *(that: Sign): Sign = Sign(
    this.zero || that.zero,
    this.neg ^ that.neg
  )

  def **(that: Int): Sign = {
    val evenPow = that % 2 == 0
    Sign(zero, if (evenPow) false.B else neg)
  }

  // LSB indicates even or oddness -- only negative if this is negative and 
  // it's raised by an odd power
  def **(that: UInt): Sign = Sign(this.zero, this.neg && that(0))
}

object Sign {
  case object Zero extends Sign(Some(true), Some(false))
  case object Positive extends Sign(Some(false), Some(false))
  case object Negative extends Sign(Some(false), Some(true))

  def apply(zero: Bool, neg: Bool): Sign = {
    val zeroLit = zero.litOption.map{_ != BigInt(0)}
    val negLit  = neg.litOption.map{_ != BigInt(0)}
    val isLit = zeroLit.isDefined && negLit.isDefined
    val wireWrapIfNotLit: Sign => Sign = s => if (isLit) { s } else Wire(s)
    val bundle = wireWrapIfNotLit(
      new Sign(zeroInit=zeroLit, negInit=negLit)
    )
    if (!zero.isLit) {
      bundle.zero := zero
    }
    if (!neg.isLit) {
      bundle.neg  := neg
    }
    bundle
  }

  implicit def apply(i: Int): Sign =
    if (i == 0) Zero else if (i > 0) Positive else Negative

  implicit def apply(i: ComparisonBundle): Sign = {
    Sign(i.eq, i.lt)
  }

  class SignAlgebra extends CMonoid[Sign] with Signed[Sign] with Order[Sign] {
    def empty: Sign = Positive
    def combine(a: Sign, b: Sign): Sign = a * b

    override def sign(a: Sign): Sign = a
    def signum(a: Sign): ComparisonBundle = ComparisonHelper(a.zero, a.neg)
    def abs(a: Sign): Sign = if (a == Negative) Positive else a
    def context_abs(a: Sign): Sign = if (a == Negative) Positive else a

    def compare(x: Sign, y: Sign): ComparisonBundle = {
      val eq = Mux(x.zero,
        // if x is zero, y must also be zero for equality
        y.zero,
        // if x is not zero, y must not be zero and must have the same sign
        !y.zero && (x.neg === y.neg)
      )
      // lt only needs to be correct when eq not true
      val lt = Mux(x.zero,
        // if x is zero, then true when y positive
        !y.zero && !y.neg,
        // if x is not zero, then true when x is negative and y not negative
        x.neg && (y.zero || !y.neg)
      )

      ComparisonHelper(eq, lt)
    }
  }

  implicit final val SignAlgebra = new SignAlgebra

  implicit final val SignMultiplicativeGroup: MultiplicativeCMonoid[Sign] =
    Multiplicative(SignAlgebra)

  //scalastyle:off method.name
  implicit def SignAction[A<: Data](implicit A: AdditiveGroup[A]): MultiplicativeAction[A, Sign] =
    new MultiplicativeAction[A, Sign] with hasContext {
      // Multiply a # by a sign
      def gtimesl(s: Sign, a: A): A = {
        Mux(ShiftRegister(s.zero, context.numAddPipes),
          ShiftRegister(A.zero, context.numAddPipes),
          Mux(ShiftRegister(s.neg, context.numAddPipes), A.negate(a), ShiftRegister(a, context.numAddPipes))
        )
      }
      def gtimesr(a: A, s: Sign): A = gtimesl(s, a)
    }
}

