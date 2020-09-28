// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3.Data

import scala.language.implicitConversions

trait EqSyntax {
  implicit def eqOps[A <: Data:Eq](a: A): EqOps[A] = new EqOps(a)
}

trait PartialOrderSyntax extends EqSyntax {
  implicit def partialOrderOps[A <: Data:PartialOrder](a: A): PartialOrderOps[A] = new PartialOrderOps(a)
}

trait OrderSyntax extends PartialOrderSyntax {
  implicit def orderOps[A <: Data:Order](a: A): OrderOps[A] = new OrderOps(a)
}

trait SignedSyntax {
  implicit def signedOps[A <: Data:Signed](a: A): SignedOps[A] = new SignedOps(a)
}

trait IsRealSyntax extends OrderSyntax with SignedSyntax {
  implicit def isRealOps[A <: Data:IsReal](a: A): IsRealOps[A] = new IsRealOps(a)
}

trait IsIntegerSyntax extends IsRealSyntax {
  implicit def isIntegerOps[A <: Data:IsIntegral](a: A): IsIntegerOps[A] = new IsIntegerOps(a)
}

trait ConvertableToSyntax {
  implicit def convertableToOps[A <: Data:ConvertableTo](a: A): ConvertableToOps[A] = new ConvertableToOps(a)
}

trait ChiselConvertableFromSyntax {
  implicit def chiselConvertableFromOps[A <: Data:ChiselConvertableFrom](a: A): ChiselConvertableFromOps[A] = new ChiselConvertableFromOps(a)
}

trait BinaryRepresentationSyntax {
  implicit def binaryRepresentationOps[A <: Data:BinaryRepresentation](a: A): BinaryRepresentationOps[A] = new BinaryRepresentationOps(a)
}

trait ContextualRingSyntax {
  implicit def contextualRingOps[A <: Data:Ring](a: A): ContextualRingOps[A] = new ContextualRingOps(a)
}