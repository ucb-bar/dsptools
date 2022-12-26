// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers.rounding

import chisel3._
import chisel3.experimental.{ChiselAnnotation, FixedPoint, RunFirrtlTransform, annotate, requireIsHardware}
import chisel3.stage.ChiselStage
import firrtl.{CircuitForm, CircuitState, HighForm, MidForm, Transform}
import firrtl.annotations.{ModuleName, SingleTargetAnnotation, Target}
import firrtl.ir.{Block, DefModule, FixedType, IntWidth, SIntType, UIntType, Module => FModule}

import scala.collection.immutable.HashMap
import scala.language.existentials

sealed trait SaturatingOp
case object SaturatingAdd extends SaturatingOp
case object SaturatingSub extends SaturatingOp

case class SaturateAnnotation(target: ModuleName, op: SaturatingOp, pipe: Int = 0) extends SingleTargetAnnotation[ModuleName] {
  def duplicate(t: ModuleName): SaturateAnnotation = this.copy(target = t)
}

case class SaturateChiselAnnotation(target: SaturateDummyModule[_ <: Data], op: SaturatingOp, pipe: Int = 0) extends ChiselAnnotation with RunFirrtlTransform {
  def toFirrtl: SaturateAnnotation = SaturateAnnotation(target.toTarget, op = op, pipe = pipe)
  def transformClass: Class[SaturateTransform] = classOf[SaturateTransform]
}

trait SaturateModule[T <: Data] extends Module {
  val a: T
  val b: T
  val c: T
}

class SaturateUIntAddModule(aWidth: Int, bWidth: Int, cWidth: Int, pipe: Int) extends SaturateModule[UInt] {
  require(pipe == 0, "pipe not implemented yet")

  val a = IO(Input(UInt(aWidth.W)))
  val b = IO(Input(UInt(bWidth.W)))
  val c = IO(Output(UInt(cWidth.W)))

  val max = ((1 << cWidth) - 1).U
  val sumWithGrow = a +& b
  val tooBig = sumWithGrow(cWidth)
  val sum = sumWithGrow(cWidth - 1, 0)

  c := Mux(tooBig, max, sum)
}

class SaturateUIntSubModule(aWidth: Int, bWidth: Int, cWidth: Int, pipe: Int) extends SaturateModule[UInt] {
  require(pipe == 0, "pipe not implemented yet")
  val a = IO(Input(UInt(aWidth.W)))
  val b = IO(Input(UInt(bWidth.W)))
  val c = IO(Output(UInt(cWidth.W)))

  val tooSmall = a < b
  val diff = a -% b

  c := Mux(tooSmall, 0.U, diff)
}

class SaturateSIntAddModule(aWidth: Int, bWidth: Int, cWidth: Int, pipe: Int) extends SaturateModule[SInt] {
  require(pipe == 0, "pipe not implemented yet")
  val a = IO(Input(SInt(aWidth.W)))
  val b = IO(Input(SInt(bWidth.W)))
  val c = IO(Output(SInt(cWidth.W)))

  val abWidth = aWidth max bWidth
  val max = ((1 << (cWidth - 1)) - 1).S
  val min = (-(1 << (cWidth - 1))).S
  val sumWithGrow = a +& b

  val tooBig = !sumWithGrow(abWidth) && sumWithGrow(abWidth - 1)
  val tooSmall = sumWithGrow(abWidth) && !sumWithGrow(abWidth - 1)

  val sum = sumWithGrow(abWidth - 1, 0).asSInt
  val fixTop = Mux(tooBig, max, sum)
  val fixTopAndBottom = Mux(tooSmall, min, fixTop)

  c := fixTopAndBottom
}

class SaturateSIntSubModule(aWidth: Int, bWidth: Int, cWidth: Int, pipe: Int) extends SaturateModule[SInt] {
  require(pipe == 0, "pipe not implemented yet")
  val a = IO(Input(SInt(aWidth.W)))
  val b = IO(Input(SInt(bWidth.W)))
  val c = IO(Output(SInt(cWidth.W)))

  val abWidth = aWidth max bWidth
  val max = ((1 << (cWidth - 1)) - 1).S
  val min = (-(1 << (cWidth - 1))).S
  val sumWithGrow = a -& b

  val tooBig = !sumWithGrow(abWidth) && sumWithGrow(abWidth - 1)
  val tooSmall = sumWithGrow(abWidth) && !sumWithGrow(abWidth - 1)

  val sum = sumWithGrow(cWidth - 1, 0).asSInt
  val fixTop = Mux(tooBig, max, sum)
  val fixTopAndBottom = Mux(tooSmall, min, fixTop)

  c := fixTopAndBottom
}

class SaturateFixedPointAddModule(
  aWidth: Int, aBP: Int,
  bWidth: Int, bBP: Int,
  cWidth: Int, cBP: Int,
  pipe: Int) extends SaturateModule[FixedPoint] {
  require(pipe == 0, "pipe not implemented yet")

  val a = IO(Input(FixedPoint(aWidth.W, aBP.BP)))
  val b = IO(Input(FixedPoint(bWidth.W, bBP.BP)))
  val c = IO(Output(FixedPoint(cWidth.W, cBP.BP)))

  
  val max = (math.pow(2, (cWidth - cBP - 1)) - math.pow(2, -cBP)).F(cWidth.W, cBP.BP)
  val min = (-math.pow(2, (cWidth - cBP - 1))).F(cWidth.W, cBP.BP)
  val sumWithGrow = a +& b

  val tooBig = !sumWithGrow(cWidth) && sumWithGrow(cWidth - 1)
  val tooSmall = sumWithGrow(cWidth) && !sumWithGrow(cWidth - 1)

  val sum = sumWithGrow(cWidth - 1, 0).asFixedPoint(cBP.BP)
  val fixTop = Mux(tooBig, max, sum)
  val fixTopAndBottom = Mux(tooSmall, min, fixTop)

  c := fixTopAndBottom
}

class SaturateFixedPointSubModule(
  aWidth: Int, aBP: Int,
  bWidth: Int, bBP: Int,
  cWidth: Int, cBP: Int,
  pipe: Int) extends SaturateModule[FixedPoint] {
  require(pipe == 0, "pipe not implemented yet")

  val a = IO(Input(FixedPoint(aWidth.W, aBP.BP)))
  val b = IO(Input(FixedPoint(bWidth.W, bBP.BP)))
  val c = IO(Output(FixedPoint(cWidth.W, cBP.BP)))
  
  val max = (math.pow(2, (cWidth - cBP - 1)) - math.pow(2, -cBP)).F(cWidth.W, cBP.BP)
  val min = (-math.pow(2, (cWidth - cBP - 1))).F(cWidth.W, cBP.BP)
  val diffWithGrow = a -& b

  val tooBig = !diffWithGrow(cWidth) && diffWithGrow(cWidth - 1)
  val tooSmall = diffWithGrow(cWidth) && !diffWithGrow(cWidth - 1)

  val diff = diffWithGrow(cWidth - 1, 0).asFixedPoint(cBP.BP)
  val fixTop = Mux(tooBig, max, diff)
  val fixTopAndBottom = Mux(tooSmall, min, fixTop)

  c := fixTopAndBottom
}

/**
 * A module that serves as a placeholder for a saturating op.
 * The frontend can't implement saturation easily when widths are unknown. This
 * module inserts a dummy op that has the desired behavior in FIRRTL's width
 * inference process. After width inference, this module will be replaced by an
 * implementation of a saturating op.
 */
class SaturateDummyModule[T <: Data](aOutside: T, bOutside: T, op: (T, T) => T) extends SaturateModule[T] {
  // this module should always be replaced in a transform
  // throw in this assertion in case it isn't
  assert(false.B)
  val a = IO(Input(chiselTypeOf(aOutside)))
  val b = IO(Input(chiselTypeOf(bOutside)))
  val res = op(a, b)
  val c = IO(Output(chiselTypeOf(res)))
  c := res
}

object Saturate {
  private def op[T <: Data](a: T, b: T, widthOp: (T, T) => T, realOp: SaturatingOp, pipe: Int = 0): T = {
    requireIsHardware(a)
    requireIsHardware(b)
    val saturate = Module(new SaturateDummyModule(a, b, widthOp))
    val anno = SaturateChiselAnnotation(saturate, realOp, pipe)
    annotate(anno)
    saturate.a := a
    saturate.b := b
    saturate.c
  }
  def addUInt(a: UInt, b: UInt, pipe: Int = 0): UInt = {
    op(a, b, { (l: UInt, r: UInt) => l +% r }, SaturatingAdd, pipe)
  }
  def addSInt(a: SInt, b: SInt, pipe: Int = 0): SInt = {
    op(a, b, { (l: SInt, r: SInt) => l +% r }, SaturatingAdd, pipe)
  }
  def addFixedPoint(a: FixedPoint, b: FixedPoint, pipe: Int = 0): FixedPoint = {
    op(a, b, { (l: FixedPoint, r: FixedPoint) => (l +& r) >> 1 }, SaturatingAdd, pipe)
  }
  def subUInt(a: UInt, b: UInt, pipe: Int = 0): UInt = {
    op(a, b, { (l: UInt, r: UInt) => l -% r }, SaturatingSub, pipe)
  }
  def subSInt(a: SInt, b: SInt, pipe: Int = 0): SInt = {
    op(a, b, { (l: SInt, r: SInt) => l -% r }, SaturatingSub, pipe)
  }
  def subFixedPoint(a: FixedPoint, b: FixedPoint, pipe: Int = 0): FixedPoint = {
    op(a, b, { (l: FixedPoint, r: FixedPoint) => (l -& r) >> 1 }, SaturatingSub, pipe)
  }
}

class SaturateTransform extends Transform {
  def inputForm: CircuitForm = MidForm
  def outputForm: CircuitForm = HighForm

  private def replaceMod(m: FModule, anno: SaturateAnnotation): FModule = {
    val aTpe = m.ports.find(_.name == "a").map(_.tpe).getOrElse(throw new Exception("a not found"))
    val bTpe = m.ports.find(_.name == "b").map(_.tpe).getOrElse(throw new Exception("b not found"))
    val cTpe = m.ports.find(_.name == "c").map(_.tpe).getOrElse(throw new Exception("c not found"))

    val newMod = (aTpe, bTpe, cTpe, anno) match {
      case (
        UIntType(IntWidth(aWidth)),
        UIntType(IntWidth(bWidth)),
        UIntType(IntWidth(cWidth)),
        SaturateAnnotation(_, SaturatingAdd, pipe)) =>
          () => new SaturateUIntAddModule(aWidth.toInt, bWidth.toInt, cWidth.toInt, pipe = pipe)
      case (
        UIntType(IntWidth(aWidth)),
        UIntType(IntWidth(bWidth)),
        UIntType(IntWidth(cWidth)),
        SaturateAnnotation(_, SaturatingSub, pipe)) =>
          () => new SaturateUIntSubModule(aWidth.toInt, bWidth.toInt, cWidth.toInt, pipe = pipe)
      case (
        SIntType(IntWidth(aWidth)),
        SIntType(IntWidth(bWidth)),
        SIntType(IntWidth(cWidth)),
        SaturateAnnotation(_, SaturatingAdd, pipe)) =>
          () => new SaturateSIntAddModule(aWidth.toInt, bWidth.toInt, cWidth.toInt, pipe = pipe)
      case (
        SIntType(IntWidth(aWidth)),
        SIntType(IntWidth(bWidth)),
        SIntType(IntWidth(cWidth)),
        SaturateAnnotation(_, SaturatingSub, pipe)) =>
          () => new SaturateSIntSubModule(aWidth.toInt, bWidth.toInt, cWidth.toInt, pipe = pipe)
      case (
        FixedType(IntWidth(aWidth), IntWidth(aBP)),
        FixedType(IntWidth(bWidth), IntWidth(bBP)),
        FixedType(IntWidth(cWidth), IntWidth(cBP)),
        SaturateAnnotation(_, SaturatingAdd, pipe)) =>
          () => new SaturateFixedPointAddModule(aWidth.toInt, aBP.toInt, bWidth.toInt, bBP.toInt, (cWidth - 1).toInt, cBP.toInt, pipe = pipe)
      case (
        FixedType(IntWidth(aWidth), IntWidth(aBP)),
        FixedType(IntWidth(bWidth), IntWidth(bBP)),
        FixedType(IntWidth(cWidth), IntWidth(cBP)),
        SaturateAnnotation(_, SaturatingSub, pipe)) =>
          () => new SaturateFixedPointSubModule(aWidth.toInt, aBP.toInt, bWidth.toInt, bBP.toInt, (cWidth - 1).toInt, cBP.toInt, pipe = pipe)
    }
    // get new body from newMod (must be single module!)

    val newBody = ChiselStage.convert(newMod()).modules.head match {
      case FModule(_, _, _, body) => body
      case _ => throw new Exception("Saw blackbox for some reason")
    }
    m.copy(body = newBody)
  }

  private def onModule(annos: Seq[SaturateAnnotation]) = {
    val annoByName: HashMap[String, SaturateAnnotation] = HashMap(annos.map({ a => a.target.name -> a }): _*)
    object SaturateAnnotation {
      def unapply(name: String): Option[SaturateAnnotation] = {
        annoByName.get(name)
      }
    }
    def onModuleInner(m: DefModule): DefModule = m match {
      case m@FModule(_, SaturateAnnotation(a), _, _) =>
        replaceMod(m, a)
      case m => m
    }
    onModuleInner(_)
  }

  def execute(state: CircuitState): CircuitState = {
    val annos = state.annotations.collect {
      case a: SaturateAnnotation => a
    }
    state.copy(circuit = state.circuit.copy(modules =
      state.circuit.modules.map(onModule(annos))))
  }
}
