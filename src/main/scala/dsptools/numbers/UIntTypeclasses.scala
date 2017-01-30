// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import chisel3.util.{ShiftRegister, Cat}
import dsptools.{hasContext, DspContext, Grow, Wrap, Saturate, DspException}
import chisel3.experimental.FixedPoint

import scala.language.implicitConversions

/**
  * Defines basic math functions for UInt
  */
trait UIntRing extends Any with Ring[UInt] with hasContext {
  def zero: UInt = 0.U
  def one: UInt = 1.U
  def plus(f: UInt, g: UInt): UInt = {
    // TODO: Saturating mux should be outside of ShiftRegister
    val sum = {
      if(context.overflowType == Grow) f +& g
      else if (context.overflowType == Wrap)  f +% g
      else throw DspException("Saturating add hasn't been implemented")
    }
    ShiftRegister(sum, context.numAddPipes)
  }
  override def minus(f: UInt, g: UInt): UInt = {
    val diff = {
      if(context.overflowType == Grow) f -& g
      else if (context.overflowType == Wrap) f -% g
      else throw DspException("Saturating subtractor hasn't been implemented")
    }
    ShiftRegister(diff, context.numAddPipes)
  }
  def negate(f: UInt): UInt = throw DspException("Can't negate UInt and get UInt")
  def times(f: UInt, g: UInt): UInt = {
    ShiftRegister(f * g, context.numMulPipes)
  }  
}

trait UIntOrder extends Any with Order[UInt] with hasContext {
  override def compare(x: UInt, y: UInt): ComparisonBundle = {
    ComparisonHelper(x === y, x < y)
  }
  override def eqv(x: UInt, y: UInt): Bool = x === y
  override def neqv(x: UInt, y:UInt): Bool = x =/= y
  override def lt(x: UInt, y: UInt): Bool = x < y
  override def lteqv(x: UInt, y: UInt): Bool = x <= y
  override def gt(x: UInt, y: UInt): Bool = x > y
  override def gteqv(x: UInt, y: UInt): Bool = x >= y
  // min, max depends on lt, gt
}

trait UIntSigned extends Any with Signed[UInt] with hasContext {
  def signum(a: UInt): ComparisonBundle = {
    ComparisonHelper(a === 0.U, a < 0.U)
  }
  def abs(a: UInt): UInt = a // UInts are unsigned!
  override def isSignZero(a: UInt): Bool = a === 0.U
  override def isSignPositive(a: UInt): Bool = !isSignZero(a)
  override def isSignNegative(a: UInt): Bool = false.B
  // isSignNonZero, isSignNonPositive, isSignNonNegative derived from above (!)
}

trait UIntIsReal extends Any with IsIntegral[UInt] with UIntOrder with UIntSigned with hasContext {
  def toDouble(a: UInt): DspReal = ???
  // In IsIntegral: ceil, floor, round, truncate (from IsReal) already defined as itself; 
  // isWhole always true
  
  def isOdd(a: UInt): Bool = a(0)
  // isEven derived from isOdd
  // Note: whatever Chisel does -- is there overflow?
  // Generally better to use your own mod if you know input bounds
  def mod(a: UInt, b: UInt): UInt = a % b
}

trait ConvertableToUInt extends ConvertableTo[UInt] with hasContext {
  def fromShort(n: Short): UInt = n.toInt.U
  def fromBigInt(n: BigInt): UInt = n.U
  def fromByte(n: Byte): UInt = n.toInt.U
  def fromDouble(n: Double): UInt = n.round.toInt.U
  def fromInt(n: Int): UInt = n.U
  def fromFloat(n: Float): UInt = n.round.toInt.U
  def fromBigDecimal(n: BigDecimal): UInt = n.doubleValue.round.toInt.U
  def fromLong(n: Long): UInt = n.U
  def fromType[B](n: B)(implicit c: ConvertableFrom[B]): UInt = c.toBigInt(n).U
  override def fromDouble(d: Double, a: UInt): UInt = {
    require(d >= 0, "Double literal to UInt needs to be >= 0")
    d.round.toInt.U
  }
  override def fromDoubleWithFixedWidth(d: Double, a: UInt): UInt = {
    require(d >= 0, "Double literal to UInt needs to be >= 0")
    val intVal = d.round.toInt  
    val intBits = BigInt(intVal).bitLength
    require(intBits <= a.getWidth, "Lit can't fit in prototype UInt bitwidth")
    intVal.asUInt(a.getWidth.W)
  }
}


// To finish:
trait ConvertableFromUInt extends ChiselConvertableFrom[UInt] with hasContext {
  // Bit grow by 1, but always positive to maintain correctness
  def intPart(a: UInt): SInt = Cat(false.B, a).asSInt
  def asFixed(a: UInt, proto: FixedPoint): FixedPoint = intPart(a).asFixedPoint(0.BP)
  //def asReal(a: A): DspReal = 
}

//Chiselbasenum
trait UIntImpl {
  implicit object UIntIntegerImpl extends UIntInteger
  //implicit def fromInt(x: Double): UInt = x.toInt.U
}

trait UIntInteger extends UIntRing with ConvertableToUInt with UIntIsReal with Integer[UInt] with hasContext {
  override def fromInt(n: Int): UInt = super[UIntRing].fromInt(n)
  
}
