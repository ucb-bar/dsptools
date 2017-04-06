// See LICENSE for license details.

package jtag.test

import chisel3.iotesters.experimental.ImplicitPokeTester

import chisel3._
import jtag._

/** Test helper object for simple conversions from string to literal.
  */
object BinaryParse {
  // from https://stackoverflow.com/questions/7197119/how-to-write-binary-literals-in-scala
  implicit class IntToBase(val digits: String) extends AnyVal {
    def base(b:Int) = Integer.parseInt(digits, b)
    def b = base(2)
    def o = base(8)
    def x = base(16)
  }
}

/** Test helper for working with Tristate Bundles and allowing Xs in expects.
  */
trait TristateTestUtility extends ImplicitPokeTester {
  import scala.language.implicitConversions

  trait TristateValue
  case object TristateLow extends TristateValue
  case object TristateHigh extends TristateValue
  case object Z extends TristateValue
  case object X extends TristateValue

  implicit def toTristateValue(x: Int) : TristateValue = {
    x match {
      case 0 => TristateLow
      case 1 => TristateHigh
    }
  }

  def check(node: Tristate, value: TristateValue, msg: String)(implicit t: InnerTester) {
    value match {
      case TristateLow => {
        check(node.driven, 1, s"$msg: expected tristate driven=1")
        check(node.data, 0, s"$msg: expected tristate data=0")
      }
      case TristateHigh => {
        check(node.driven, 1, s"$msg: expected tristate driven=1")
        check(node.data, 1, s"$msg: expected tristate data=1")
      }
      case Z => {
        check(node.driven, 0, s"$msg: expected tristate driven=0")
      }
      case X =>
    }
  }

  def poke(node: Bool, value: TristateValue)(implicit t: InnerTester) {
    value match {
      case TristateLow => t.poke(node, 0)
      case TristateHigh => t.poke(node, 1)
      case X => t.poke(node, 0)
    }
  }
}
