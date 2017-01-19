// See LICENSE for license details.

package dsptools.numbers

import chisel3._

/**
  * Much of this is drawn from non/spire, but using Chisel Bools instead of
  * Java Bools. I suppose a more general solution would be generic in
  * return type, but the use cases there seem obscure.
  */

/**
  * A simple type class for numeric types that are a subset of the reals.
  */
trait IsReal[A<:Data] extends Any with Order[A] with Signed[A] {

  /**
    * Rounds `a` the nearest integer that is greater than or equal to `a`.
    */
  def ceil(a: A): A

  /**
    * Rounds `a` the nearest integer that is less than or equal to `a`.
    */
  def floor(a: A): A

  /**
    * Rounds `a` to the nearest integer.
    */
  def round(a: A): A

  /**
    * Returns `true` iff `a` is a an integer.
    */
  def isWhole(a: A): Bool

  /**
    * Approximates `a` as a `Double`.
    * This is problematic conversion from FixedPoint
    * to DspReal, where inference can make computing this
    * difficult
    */
//  def toDouble(a: A): DspReal
}

object IsReal {
  def apply[A<:Data](implicit A: IsReal[A]): IsReal[A] = A
}

trait IsAlgebraic[A<:Data] extends Any with IsReal[A] {
  //def toAlgebraic(a: A): Algebraic
  //def toDouble(a: A): DspReal = DspReal(toAlgebraic(a).toDouble) // ???
}
object IsAlgebraic {
  def apply[A<:Data](implicit A: IsAlgebraic[A]): IsAlgebraic[A] = A
}

trait IsRational[A<:Data] extends Any with IsAlgebraic[A] {
  //def toRational(a: A): Rational
  //def toAlgebraic(a: A): Algebraic = ???// Algebraic(toRational(a))
}

object IsRational {
  def apply[A<:Data](implicit A: IsRational[A]): IsRational[A] = A
}

trait IsIntegral[A<:Data] extends Any with IsRational[A] {
  def ceil(a: A): A = a
  def floor(a: A): A = a
  def round(a: A): A = a
  def isWhole(a: A): Bool = true.B
  def mod(a: A, b: A): A
}

object IsIntegral {
  def apply[A<:Data](implicit A: IsIntegral[A]): IsIntegral[A] = A
}

trait Real[A<:Data] extends Any with Ring[A] with ConvertableTo[A] with IsReal[A] {
  def fromRational(a: spire.math.Rational): A = fromDouble(a.toDouble)
  def fromAlgebraic(a: spire.math.Algebraic): A = fromDouble(a.toDouble)
  def fromReal(a: spire.math.Real): A = fromDouble(a.toDouble)
}

