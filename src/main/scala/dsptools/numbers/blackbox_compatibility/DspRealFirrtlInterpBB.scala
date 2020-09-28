// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import firrtl.ir.Type
import firrtl_interpreter._
import treadle.{ScalaBlackBox, ScalaBlackBoxFactory}

trait DspBlackBlackBoxImpl extends BlackBoxImplementation with ScalaBlackBox

abstract class DspRealTwoArgumentToDouble extends DspBlackBlackBoxImpl {
  /**
    * sub-classes must implement this two argument function
    *
    * @param double1 first operand
    * @param double2 second operand
    * @return        double operation result
    */
  def twoOp(double1: Double, double2: Double): Double

  def outputDependencies(outputName: String): Seq[(String)] = {
    outputName match {
      case "out" => Seq("in1", "in2")
      case _ => Seq.empty
    }
  }

  override def getOutput(inputValues: Seq[BigInt], tpe: Type, outputName: String): BigInt = {
    val arg1 :: arg2 :: _ = inputValues
    val doubleArg1 = bigIntBitsToDouble(arg1)
    val doubleArg2 = bigIntBitsToDouble(arg2)
    val doubleResult = twoOp(doubleArg1, doubleArg2)
    doubleToBigIntBits(doubleResult)
  }

  def cycle(): Unit = {}

  def execute(inputValues: Seq[Concrete], tpe: Type, outputName: String): Concrete = {
    val arg1 :: arg2 :: _ = inputValues
    val doubleArg1 = bigIntBitsToDouble(arg1.value)
    val doubleArg2 = bigIntBitsToDouble(arg2.value)
    val doubleResult = twoOp(doubleArg1, doubleArg2)
    val result = doubleToBigIntBits(doubleResult)
    ConcreteSInt(result, DspReal.underlyingWidth, arg1.poisoned || arg2.poisoned).asUInt
  }
}

abstract class DspRealOneArgumentToDouble extends DspBlackBlackBoxImpl {
  /**
    * sub-classes must implement this two argument function
    *
    * @param double1 first operand
    * @return        double operation result
    */
  def oneOp(double1: Double): Double

  def outputDependencies(outputName: String): Seq[(String)] = {
    outputName match {
      case "out" => Seq("in")
      case _ => Seq.empty
    }
  }
  def cycle(): Unit = {}

  def execute(inputValues: Seq[Concrete], tpe: Type, outputName: String): Concrete = {
    val arg1 :: _ = inputValues
    val doubleArg1 = bigIntBitsToDouble(arg1.value)
    val doubleResult = oneOp(doubleArg1)
    val result = doubleToBigIntBits(doubleResult)
    ConcreteSInt(result, DspReal.underlyingWidth, arg1.poisoned).asUInt
  }

  def getOutput(inputValues: Seq[BigInt], tpe: Type, outputName: String): BigInt = {
    val arg1 :: _ = inputValues
    val doubleArg1 = bigIntBitsToDouble(arg1)
    val doubleResult = oneOp(doubleArg1)
    doubleToBigIntBits(doubleResult)
  }
}

abstract class DspRealTwoArgumentToBoolean extends DspBlackBlackBoxImpl {
  /**
    * sub-classes must implement this two argument function
    *
    * @param double1 first operand
    * @param double2 second operand
    * @return        boolean operation result
    */
  def twoOp(double1: Double, double2: Double): Boolean

  def outputDependencies(outputName: String): Seq[(String)] = {
    outputName match {
      case "out" => Seq("in1", "in2")
      case _ => Seq.empty
    }
  }
  def cycle(): Unit = {}

  def getOutput(inputValues: Seq[BigInt], tpe: Type, outputName: String): BigInt = {
    val arg1 :: arg2 :: _ = inputValues
    val doubleArg1 = bigIntBitsToDouble(arg1)
    val doubleArg2 = bigIntBitsToDouble(arg2)
    val booleanResult = twoOp(doubleArg1, doubleArg2)
    if (booleanResult) Big1 else Big0
  }

  def execute(inputValues: Seq[Concrete], tpe: Type, outputName: String): Concrete = {
    val arg1 :: arg2 :: _ = inputValues
    val doubleArg1 = bigIntBitsToDouble(arg1.value)
    val doubleArg2 = bigIntBitsToDouble(arg2.value)
    val booleanResult = twoOp(doubleArg1, doubleArg2)
    val result = if (booleanResult) Big1 else Big0
    TypeInstanceFactory(tpe, result, arg1.poisoned || arg2.poisoned)
  }
}

class DspRealAdd(val name: String) extends DspRealTwoArgumentToDouble {
  def twoOp(double1: Double, double2: Double): Double = double1 + double2
}

class DspRealSubtract(val name: String) extends DspRealTwoArgumentToDouble {
  def twoOp(double1: Double, double2: Double): Double = double1 - double2
}

class DspRealMultiply(val name: String) extends DspRealTwoArgumentToDouble {
  def twoOp(double1: Double, double2: Double): Double = double1 * double2
}

class DspRealDivide(val name: String) extends DspRealTwoArgumentToDouble {
  def twoOp(double1: Double, double2: Double): Double = double1 / double2
}

class DspRealGreaterThan(val name: String) extends DspRealTwoArgumentToBoolean {
  def twoOp(double1: Double, double2: Double): Boolean = double1 > double2
}

class DspRealGreaterThanEquals(val name: String) extends DspRealTwoArgumentToBoolean {
  def twoOp(double1: Double, double2: Double): Boolean = double1 >= double2
}

class DspRealLessThan(val name: String) extends DspRealTwoArgumentToBoolean {
  def twoOp(double1: Double, double2: Double): Boolean = double1 < double2
}

class DspRealLessThanEquals(val name: String) extends DspRealTwoArgumentToBoolean {
  def twoOp(double1: Double, double2: Double): Boolean = double1 <= double2
}

class DspRealEquals(val name: String) extends DspRealTwoArgumentToBoolean {
  def twoOp(double1: Double, double2: Double): Boolean = double1 == double2
}

class DspRealNotEquals(val name: String) extends DspRealTwoArgumentToBoolean {
  def twoOp(double1: Double, double2: Double): Boolean = double1 != double2
}

// Angie: Let's rely on type classes to get this behavior instead
/*
class DspRealIntPart(val name: String) extends DspRealOneArgumentToDouble {
  def oneOp(double1: Double): Double = double1.toInt.toDouble
}
*/

/** Math operations from IEEE.1364-2005 **/
class DspRealLn(val name: String)  extends DspRealOneArgumentToDouble {
  def oneOp(double1: Double): Double = math.log(double1)
}

class DspRealLog10(val name: String)  extends DspRealOneArgumentToDouble {
  def oneOp(double1: Double): Double = math.log10(double1)
}

class DspRealExp(val name: String)  extends DspRealOneArgumentToDouble {
  def oneOp(double1: Double): Double = math.exp(double1)
}

class DspRealSqrt(val name: String)  extends DspRealOneArgumentToDouble {
  def oneOp(double1: Double): Double = math.sqrt(double1)
}

class DspRealPow(val name: String)  extends DspRealTwoArgumentToDouble {
  def twoOp(double1: Double, double2: Double): Double = math.pow(double1, double2)
}

class DspRealFloor(val name: String)  extends DspRealOneArgumentToDouble {
  def oneOp(double1: Double): Double = math.floor(double1)
}

class DspRealCeil(val name: String)  extends DspRealOneArgumentToDouble {
  def oneOp(double1: Double): Double = math.ceil(double1)
}

class DspRealSin(val name: String)  extends DspRealOneArgumentToDouble {
  def oneOp(double1: Double): Double = math.sin(double1)
}

class DspRealCos(val name: String)  extends DspRealOneArgumentToDouble {
  def oneOp(double1: Double): Double = math.cos(double1)
}

class DspRealTan(val name: String)  extends DspRealOneArgumentToDouble {
  def oneOp(double1: Double): Double = math.tan(double1)
}

class DspRealASin(val name: String)  extends DspRealOneArgumentToDouble {
  def oneOp(double1: Double): Double = math.asin(double1)
}

class DspRealACos(val name: String)  extends DspRealOneArgumentToDouble {
  def oneOp(double1: Double): Double = math.acos(double1)
}

class DspRealATan(val name: String)  extends DspRealOneArgumentToDouble {
  def oneOp(double1: Double): Double = math.atan(double1)
}

class DspRealATan2(val name: String)  extends DspRealTwoArgumentToDouble {
  def twoOp(double1: Double, double2: Double): Double = math.atan2(double1, double2)
}

class DspRealHypot(val name: String)  extends DspRealTwoArgumentToDouble {
  def twoOp(double1: Double, double2: Double): Double = math.hypot(double1, double2)
}

class DspRealSinh(val name: String)  extends DspRealOneArgumentToDouble {
  def oneOp(double1: Double): Double = math.sinh(double1)
}

class DspRealCosh(val name: String)  extends DspRealOneArgumentToDouble {
  def oneOp(double1: Double): Double = math.cosh(double1)
}

class DspRealTanh(val name: String)  extends DspRealOneArgumentToDouble {
  def oneOp(double1: Double): Double = math.tanh(double1)
}

class DspRealASinh(val name: String)  extends DspRealOneArgumentToDouble {
  def oneOp(double1: Double): Double = math.log(double1 + math.sqrt(double1 * double1 + 1))
}

class DspRealACosh(val name: String)  extends DspRealOneArgumentToDouble {
  def oneOp(double1: Double): Double = math.log(double1 + math.sqrt(double1 * double1 - 1))
}

class DspRealATanh(val name: String)  extends DspRealOneArgumentToDouble {
  def oneOp(double1: Double): Double = 0.5 * math.log((1 + double1) / (1 - double1))
}

class DspRealToInt(val name: String) extends DspBlackBlackBoxImpl {
  def outputDependencies(outputName: String): Seq[(String)] = {
    outputName match {
      case "out" => Seq("in")
      case _ => Seq.empty
    }
  }
  def cycle(): Unit = {}

  def execute(inputValues: Seq[Concrete], tpe: Type, outputName: String): Concrete = {
    val arg1 :: _ = inputValues
    val result = arg1.value
    TypeInstanceFactory(tpe, result)
  }

  def getOutput(inputValues: Seq[BigInt], tpe: Type, outputName: String): BigInt = {
    val arg1 :: _ = inputValues
    arg1
  }
}

class DspRealFromInt(val name: String) extends DspBlackBlackBoxImpl {
  def outputDependencies(outputName: String): Seq[(String)] = {
    outputName match {
      case "out" => Seq("in")
      case _ => Seq.empty
    }
  }
  def cycle(): Unit = {}

  def getOutput(inputValues: Seq[BigInt], tpe: Type, outputName: String): BigInt = {
    val arg1 :: _ = inputValues
    arg1
  }

  def execute(inputValues: Seq[Concrete], tpe: Type, outputName: String): Concrete = {
    val arg1 :: _ = inputValues
    val result = arg1.value
    TypeInstanceFactory(tpe, result)
  }
}

//scalastyle:off cyclomatic.complexity
class DspRealFactory extends BlackBoxFactory {
  def createInstance(instanceName: String, blackBoxName: String): Option[BlackBoxImplementation] = {
    blackBoxName match {
      case "BBFAdd"               => Some(add(new DspRealAdd(instanceName)))
      case "BBFSubtract"          => Some(add(new DspRealSubtract(instanceName)))
      case "BBFMultiply"          => Some(add(new DspRealMultiply(instanceName)))
      case "BBFDivide"            => Some(add(new DspRealDivide(instanceName)))
      case "BBFLessThan"          => Some(add(new DspRealLessThan(instanceName)))
      case "BBFLessThanEquals"    => Some(add(new DspRealLessThanEquals(instanceName)))
      case "BBFGreaterThan"       => Some(add(new DspRealGreaterThan(instanceName)))
      case "BBFGreaterThanEquals" => Some(add(new DspRealGreaterThanEquals(instanceName)))
      case "BBFEquals"            => Some(add(new DspRealEquals(instanceName)))
      case "BBFNotEquals"         => Some(add(new DspRealNotEquals(instanceName)))
      case "BBFFromInt"           => Some(add(new DspRealFromInt(instanceName)))
      case "BBFToInt"             => Some(add(new DspRealToInt(instanceName)))
      //case "BBFIntPart"           => Some(add(new DspRealIntPart(instanceName)))
      case "BBFLn"                => Some(add(new DspRealLn(instanceName)))
      case "BBFLog10"             => Some(add(new DspRealLog10(instanceName)))
      case "BBFExp"               => Some(add(new DspRealExp(instanceName)))
      case "BBFSqrt"              => Some(add(new DspRealSqrt(instanceName)))
      case "BBFPow"               => Some(add(new DspRealPow(instanceName)))
      case "BBFFloor"             => Some(add(new DspRealFloor(instanceName)))
      case "BBFCeil"              => Some(add(new DspRealCeil(instanceName)))
      case "BBFSin"               => Some(add(new DspRealSin(instanceName)))
      case "BBFCos"               => Some(add(new DspRealCos(instanceName)))
      case "BBFTan"               => Some(add(new DspRealTan(instanceName)))
      case "BBFASin"              => Some(add(new DspRealASin(instanceName)))
      case "BBFACos"              => Some(add(new DspRealACos(instanceName)))
      case "BBFATan"              => Some(add(new DspRealATan(instanceName)))
      case "BBFATan2"             => Some(add(new DspRealATan2(instanceName)))
      case "BBFHypot"             => Some(add(new DspRealHypot(instanceName)))
      case "BBFSinh"              => Some(add(new DspRealSinh(instanceName)))
      case "BBFCosh"              => Some(add(new DspRealCosh(instanceName)))
      case "BBFTanh"              => Some(add(new DspRealTanh(instanceName)))
      case "BBFASinh"             => Some(add(new DspRealASinh(instanceName)))
      case "BBFACosh"             => Some(add(new DspRealACosh(instanceName)))
      case "BBFATanh"             => Some(add(new DspRealATanh(instanceName)))
      case _                      => None
    }
  }
}

class TreadleDspRealFactory extends ScalaBlackBoxFactory {
  def createInstance(instanceName: String, blackBoxName: String): Option[ScalaBlackBox] = {
    blackBoxName match {
      case "BBFAdd"               => Some(add(new DspRealAdd(instanceName)))
      case "BBFSubtract"          => Some(add(new DspRealSubtract(instanceName)))
      case "BBFMultiply"          => Some(add(new DspRealMultiply(instanceName)))
      case "BBFDivide"            => Some(add(new DspRealDivide(instanceName)))
      case "BBFLessThan"          => Some(add(new DspRealLessThan(instanceName)))
      case "BBFLessThanEquals"    => Some(add(new DspRealLessThanEquals(instanceName)))
      case "BBFGreaterThan"       => Some(add(new DspRealGreaterThan(instanceName)))
      case "BBFGreaterThanEquals" => Some(add(new DspRealGreaterThanEquals(instanceName)))
      case "BBFEquals"            => Some(add(new DspRealEquals(instanceName)))
      case "BBFNotEquals"         => Some(add(new DspRealNotEquals(instanceName)))
      case "BBFFromInt"           => Some(add(new DspRealFromInt(instanceName)))
      case "BBFToInt"             => Some(add(new DspRealToInt(instanceName)))
      //case "BBFIntPart"           => Some(add(new DspRealIntPart(instanceName)))
      case "BBFLn"                => Some(add(new DspRealLn(instanceName)))
      case "BBFLog10"             => Some(add(new DspRealLog10(instanceName)))
      case "BBFExp"               => Some(add(new DspRealExp(instanceName)))
      case "BBFSqrt"              => Some(add(new DspRealSqrt(instanceName)))
      case "BBFPow"               => Some(add(new DspRealPow(instanceName)))
      case "BBFFloor"             => Some(add(new DspRealFloor(instanceName)))
      case "BBFCeil"              => Some(add(new DspRealCeil(instanceName)))
      case "BBFSin"               => Some(add(new DspRealSin(instanceName)))
      case "BBFCos"               => Some(add(new DspRealCos(instanceName)))
      case "BBFTan"               => Some(add(new DspRealTan(instanceName)))
      case "BBFASin"              => Some(add(new DspRealASin(instanceName)))
      case "BBFACos"              => Some(add(new DspRealACos(instanceName)))
      case "BBFATan"              => Some(add(new DspRealATan(instanceName)))
      case "BBFATan2"             => Some(add(new DspRealATan2(instanceName)))
      case "BBFHypot"             => Some(add(new DspRealHypot(instanceName)))
      case "BBFSinh"              => Some(add(new DspRealSinh(instanceName)))
      case "BBFCosh"              => Some(add(new DspRealCosh(instanceName)))
      case "BBFTanh"              => Some(add(new DspRealTanh(instanceName)))
      case "BBFASinh"             => Some(add(new DspRealASinh(instanceName)))
      case "BBFACosh"             => Some(add(new DspRealACosh(instanceName)))
      case "BBFATanh"             => Some(add(new DspRealATanh(instanceName)))
      case _                      => None
    }
  }
}
