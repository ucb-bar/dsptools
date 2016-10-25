// See LICENSE for license details.

package dsptools.numbers

trait AllSyntax extends EqSyntax with PartialOrderSyntax with OrderSyntax with IsRealSyntax with SignedSyntax with
  ConvertableToSyntax

trait AllImpl extends SIntImpl with FixedPointImpl with DspRealImpl with DspComplexImpl

object implicits extends AllSyntax with AllImpl with spire.syntax.AllSyntax
