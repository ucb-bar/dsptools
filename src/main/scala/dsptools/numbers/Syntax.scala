// See LICENSE for license details.

package dsptools.numbers

import chisel3.Data

import scala.language.implicitConversions

/**
  * Much of this is drawn from non/spire, but using Chisel Bools instead of
  * Java Bools. I suppose a more general solution would be generic in
  * return type, but the use cases there seem obscure.
  */

trait EqSyntax {
  implicit def eqOps[A<:Data:Eq](a:A): EqOps[A] = new EqOps(a)
}

trait PartialOrderSyntax extends EqSyntax {
  implicit def partialOrderOps[A<:Data:PartialOrder](a:A): PartialOrderOps[A] = new PartialOrderOps(a)
}

trait OrderSyntax extends PartialOrderSyntax {
  implicit def orderOps[A<:Data:Order](a:A): OrderOps[A] = new OrderOps(a)
}

trait IsRealSyntax extends OrderSyntax with SignedSyntax {
  implicit def isRealOps[A<:Data:IsReal](a:A): IsRealOps[A] = new IsRealOps(a)
}

trait SignedSyntax {
  implicit def signedOps[A<:Data: Signed](a: A): SignedOps[A] = new SignedOps(a)
}

trait ConvertableToSyntax {
  def fromDouble[T<:Data:ConvertableTo](a: Double): T = implicitly[ConvertableTo[T]].fromDouble(a)
}

