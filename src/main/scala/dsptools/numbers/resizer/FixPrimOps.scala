// See LICENSE for license details.

package dsptools.numbers.resizer

import firrtl.Mappers._
import firrtl.PrimOps._
import firrtl.bitWidth
import firrtl.ir._
import firrtl.passes.Pass

// Makes all implicit width extensions and truncations explicit
object FixPrimOps extends Pass {
  private def width(t: Type): Int = bitWidth(t).toInt
  private def width(e: Expression): Int = width(e.tpe)

  var changesMade: Int = 0

  def hasWidth(tpe: Type): Boolean = tpe match {
    case UIntType(IntWidth(_)) => true
    case SIntType(IntWidth(_)) => true
    case ClockType             => true
    case _                     => false
  }

  // Returns an expression with the correct integer width
  private def fixup(i: Int)(e: Expression) = {
    def tx = e.tpe match {
      case _: UIntType => UIntType(IntWidth(i))
      case _: SIntType => SIntType(IntWidth(i))
      // default case should never be reached
    }
    width(e) match {
      case j if i > j => DoPrim(Pad, Seq(e), Seq(i), tx)
      case j if i < j =>
        val e2 = DoPrim(Bits, Seq(e), Seq(i - 1, 0), UIntType(IntWidth(i)))
        // Bit Select always returns UInt, cast if selecting from SInt
        e.tpe match {
          case UIntType(_) => e2
          case SIntType(_) => DoPrim(AsSInt, Seq(e2), Seq.empty, SIntType(IntWidth(i)))
        }
      case _ => e
    }
  }

  // Recursive, updates expression so children exp's have correct widths
  private def onExp(e: Expression): Expression = e map onExp match {
    case DoPrim(Bits, Seq(a), Seq(hi, lo), tpe) if hasWidth(a.tpe) && bitWidth(a.tpe) <= hi =>
      changesMade += 1
      val newHi = bitWidth(a.tpe) - 1
      val adjustedBits = DoPrim(Bits, Seq(a), Seq(newHi, lo), tpe)
      //scalastyle:off regex
      // println(s"Adjusting bits for ${a} was ($hi, $lo) now ($newHi, $lo)")
      adjustedBits
    case ex => ex
  }

  // Recursive. Fixes assignments and register initialization widths
  private def onStmt(s: Statement): Statement = s map onExp match {
    case sx: Connect =>
      sx.copy(expr = fixup(width(sx.loc))(sx.expr))
    case sx: DefRegister =>
      sx.copy(init = fixup(width(sx.tpe))(sx.init))
    case sx => sx map onStmt
  }

  def run(c: Circuit): Circuit = {
    val newCircuit = c copy (modules = c.modules map (_ map onStmt))
    //scalastyle:off regex
    println(s"FixBits: changes made $changesMade")
    newCircuit
  }
}
