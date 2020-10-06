// SPDX-License-Identifier: Apache-2.0

package dsptools

package object numbers extends AllSyntax with AllImpl with spire.syntax.RingSyntax
/*with spire.syntax.AllSyntax*/ {
  type AdditiveGroup[T]           = spire.algebra.AdditiveGroup[T]
  type CMonoid[T]                 = spire.algebra.CMonoid[T]
  type ConvertableFrom[T]         = spire.math.ConvertableFrom[T]
  type Field[T]                   = spire.algebra.Field[T]
  type MultiplicativeAction[T, U] = spire.algebra.MultiplicativeAction[T, U]
  type MultiplicativeCMonoid[T]   = spire.algebra.MultiplicativeCMonoid[T]

  val Multiplicative              = spire.algebra.Multiplicative

  // rounding aliases
  val Floor                       = RoundDown
  val Ceiling                     = RoundUp
  val Convergent                  = RoundHalfToEven
  val Round                       = RoundHalfTowardsInfinity
}
