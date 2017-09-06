// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import chisel3.util.ShiftRegister
import dsptools.hasContext

import scala.language.implicitConversions

class SignBundle extends Bundle {
  val zero = Bool()
  // ignore neg if zero is true
  val neg  = Bool()
}

/**
  * Much of this is drawn from non/spire, but using Chisel Bools instead of
  * Java Bools. I suppose a more general solution would be generic in
  * return type, but the use cases there seem obscure.
  */

/**
  * A simple ADT representing the `Sign` of an object.
  */
sealed class Sign(that: SignBundle) extends SignBundle {
  import Sign._

  zero := that.zero
  neg  := that.neg

  def unary_-(): Sign = new Sign(Sign(this.zero, !this.neg))

  def *(that: Sign): Sign = new Sign(Sign(
    this.zero || that.zero,
    this.neg ^ that.neg
  ))

  def **(that: Int): Sign = **(UInt(that.W))
  // LSB indicates even or oddness -- only negative if this is negative and 
  // it's raised by an odd power
  def **(that: UInt): Sign = new Sign(Sign(this.zero, this.neg && that(0)))
}

object Sign {
  case object Zero extends Sign(Sign(true.B, false.B))
  case object Positive extends Sign(Sign(false.B, false.B))
  case object Negative extends Sign(Sign(false.B, true.B))

  def apply(zero: Bool, neg: Bool): SignBundle = {
    val bundle = new SignBundle
    bundle.zero := zero
    bundle.neg  := neg
    bundle
  }

  implicit def apply(i: Int): Sign =
    if (i == 0) Zero else if (i > 0) Positive else Negative

  implicit def apply(i: ComparisonBundle): Sign = {
    val bundle = new SignBundle
    bundle.zero := i.eq
    bundle.neg := i.lt
    new Sign(bundle)
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

