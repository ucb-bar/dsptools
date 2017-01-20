// See LICENSE for license details.

package dsptools.numbers

import chisel3.experimental.FixedPoint

trait AllSyntax extends EqSyntax with PartialOrderSyntax with OrderSyntax with IsRealSyntax with SignedSyntax with
  ConvertableToSyntax

trait AllImpl extends SIntImpl with FixedPointImpl with DspRealImpl with DspComplexImpl

object implicits extends AllSyntax with AllImpl with spire.syntax.AllSyntax {

  import chisel3._
  import scala.language.implicitConversions

  implicit def realDouble2T[T <: Data:Real](real: T): RealPML[T] = new RealPML(real)

  class RealPML[T <: Data:Real](real: T) {
    /** Generates a type-parameterized Lit from a Scala Double. 
        Gets the binary point from the prototype and infers the width.
    */
    def double2T(dbl: Double): T = {
      val out = real match {
        case f: FixedPoint => FixedPoint.fromDouble(dbl, binaryPoint = f.binaryPoint.get)
        case s: SInt => math.round(dbl).toInt.S
        case r: DspReal => DspReal(dbl)
      }
      out.asInstanceOf[T]
    }
    /** Generates a type-parameterized Lit from a Scala Double. 
        Gets the width & binary point from the prototype 
        (for things like LUTs that require constant width).
    */
    def double2TFixedWidth(dbl: Double): T = {
      val errMsg = "Constant can't fit in prototype bitwidth for double2TFixedWidth"
      val out = real match {
        case f: FixedPoint => {
          val sintBits = BigInt(dbl.toInt).bitLength + 1
          require(sintBits + f.binaryPoint.get <= f.getWidth, 
              errMsg)
          FixedPoint.fromDouble(dbl, width = f.getWidth, binaryPoint = f.binaryPoint.get)
        }
        case s: SInt => {
          val intVal = math.round(dbl).toInt
          val intBits = BigInt(intVal).bitLength + 1
          require(intBits <= s.getWidth, errMsg)
          intVal.asSInt(s.getWidth.W)
        }
        case r: DspReal => DspReal(dbl)
      }
      out.asInstanceOf[T]
    }
  }
}