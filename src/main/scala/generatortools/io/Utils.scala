package generatortools.io

import chisel3._
import chisel3.core.DataMirror
import chisel3.experimental._
import chisel3.internal.firrtl._
import chisel3.util.Cat
import dsptools.numbers._
import firrtl.passes.IsKnown

object ConvertType {
  /** Convert special type (i.e. Clock, Interval) to equivalent base Chisel type.
    * Otherwise, keep the current base Chisel type. Warning: Any kind of Record becomes a CustomBundle!
    */
  def apply[T <: Data](tpe: T): Data = {
    val newTpe = tpe match {
      // TODO: special case others
      case s: SInt =>
        s.widthOption match {
          case None => SInt(64.W)
          case Some(width) => SInt(width.W)
        }
      case f: FixedPoint =>
        (f.widthOption, f.binaryPoint) match {
          case (None, UnknownBinaryPoint) => FixedPoint(128.W, 64.BP)
          case (None, KnownBinaryPoint(bp)) => FixedPoint((64 + bp).W, bp.BP)
          case (Some(width), KnownBinaryPoint(bp)) => FixedPoint(width.W, bp.BP)
        }
      // DspReal = Bundle; needs to have precedence
      case  _: Bool | _: UInt | _: DspReal => tpe
      // TODO: Handle Reset once that becomes a thing
      case _: Clock => Bool()
      // Recursive aggregate handling
      case v: Vec[_] => Vec(v.getElements.map(apply(_)))
      case c: DspComplex[_] =>
        (c.real, c.imag) match {
          case (r: UInt, i: UInt) => DspComplex(apply(r).asInstanceOf[UInt], apply(i).asInstanceOf[UInt])
          case (r: SInt, i: SInt) => DspComplex(apply(r).asInstanceOf[SInt], apply(i).asInstanceOf[SInt])
          case (r: FixedPoint, i: FixedPoint) => DspComplex(apply(r).asInstanceOf[FixedPoint], apply(i).asInstanceOf[FixedPoint])
          case (r: DspReal, i: DspReal) => DspComplex(apply(r).asInstanceOf[DspReal], apply(i).asInstanceOf[DspReal])
          case (r: Interval, i: Interval) => DspComplex(apply(r).asInstanceOf[FixedPoint], apply(i).asInstanceOf[FixedPoint])
        }
      case r: Record => new CustomBundle(r.elements.toList.map { case (field, elt) => field -> apply(elt) }: _*)
      // Get equivalent base type from Interval type
      case i: Interval =>
        (i.range.getWidth, i.range.binaryPoint, i.range.lower) match {
          case (UnknownWidth(), UnknownBinaryPoint, _) =>
            // Make a very large FP placeholder when W, BP are unknown
            FixedPoint(128.W, 64.BP)
          case (UnknownWidth(), KnownBinaryPoint(bp), _) if bp > 0 =>
            // Make a large FP placeholder when W is unknown & fractional bits are used
            FixedPoint((64 + bp).W, bp.BP)
          case (UnknownWidth(), KnownBinaryPoint(bp), lower: IsKnown) if bp == 0 && lower.value >= 0 =>
            // Make large UInt when W is unknown, but i is an integer with lower bound >= 0
            UInt(64.W)
          case (UnknownWidth(), KnownBinaryPoint(bp), _) if bp == 0 =>
            // Make large SInt when W is unknown and lower bound is unknown
            SInt(64.W)
          case (KnownWidth(width), KnownBinaryPoint(bp), lower: IsKnown) if bp > 0 =>
            // Equivalent FixedPoint representation
            FixedPoint(width.W, bp.BP)
          case (KnownWidth(width), KnownBinaryPoint(bp), lower: IsKnown) if bp == 0 && lower.value < 0 =>
            // Equivalent SInt representation
            SInt(width.W)
          case (KnownWidth(width), KnownBinaryPoint(bp), lower: IsKnown) if bp == 0 && lower.value >= 0 =>
            // Equivalent UInt representation -- Interval is always signed
            UInt((width - 1).W)
          case (_, _, _) =>
            throw new Exception(s"Invalid type to convert $tpe")
        }
    }
    // Match data direction
    if (CheckDirection.isInput(tpe)) Input(newTpe)
    else if (CheckDirection.isOutput(tpe)) Output(newTpe)
    else newTpe
  }
}

object CheckDirection {
  /** Is data Input? */
  def isInput(d: Data): Boolean = matchDirection(d)._1
  /** Is data Output? */
  def isOutput(d: Data): Boolean = matchDirection(d)._2
  /** Checks if Element is input, output, or neither. Aggregates are always neither. */
  private def matchDirection(d: Data): (Boolean, Boolean) = d match {
    // TODO: Make consistent with new Chisel
    case e: Element =>
      e.dir match {
        case core.Direction.Input => (true, false)
        case core.Direction.Output => (false, true)
        case _ => (false, false)
      }
    // Fake base type
    case r: DspReal =>
      r.node.dir match {
        case core.Direction.Input => (true, false)
        case core.Direction.Output => (false, true)
        case _ => (false, false)
      }
    case c: DspComplex[_] =>
      val (in1, out1) = matchDirection(c.real)
      val (in2, out2) = matchDirection(c.imag)
      (in1 & in2, out1 & out2)
    case _ => (false, false)
  }
}

object Connect {
  /** Connect similar signals. Hierarchy should be identical (esp. for Records). This enables you to, for example,
    * connect an Interval signal with an equivalent base Chisel signal. globalClk will be passed in to
    * all clock inputs, if provided.
    */
  def apply[T <: Data, U <: Data](left: T, right: U, globalClk: Option[Clock] = None): Unit = {
    val leftIsInput = CheckDirection.isInput(left)
    val leftIsOutput = CheckDirection.isOutput(left)
    (left, right) match {
      // Recursive aggregate handling
      case (leftAgg: Aggregate, rightAgg: Aggregate) =>
        leftAgg.getElements.zip(rightAgg.getElements) foreach { case (leftElt, rightElt) =>
          apply(leftElt, rightElt, globalClk)
        }
      // Connect Interval type to base type
      case (leftU: UInt, rightI: Interval) if leftIsInput =>
        rightI := Cat(0.U(1.W), leftU).asInterval(rightI.range)
      case (leftU: UInt, rightI: Interval) if leftIsOutput =>
        leftU := rightI.asUInt
      case (leftS: SInt, rightI: Interval) if leftIsInput =>
        rightI := leftS.asInterval(rightI.range)
      case (leftS: SInt, rightI: Interval) if leftIsOutput =>
        leftS := rightI.asSInt
      case (leftF: FixedPoint, rightI: Interval) if leftIsInput =>
        rightI := leftF.asInterval(IntervalRange(rightI.range.lower, rightI.range.upper, leftF.binaryPoint))
      case (leftF: FixedPoint, rightI: Interval) if leftIsOutput =>
        // Note that the BP has to be taken from rightI; otherwise there can be
        // binary point misalignment
        leftF := rightI.asFixedPoint(rightI.binaryPoint)
      // Connect up clocks (external vs. global)
      case (leftB: Bool, rightC: Clock) if leftIsInput && globalClk.isEmpty =>
        rightC := leftB.asClock
      case (leftB: Bool, rightC: Clock) if leftIsInput && globalClk.isDefined =>
        rightC := globalClk.get
      case (leftB: Bool, rightC: Clock) if leftIsOutput =>
        leftB := rightC.asUInt.toBool
      case (_, _) =>
        // Assumes top and internal base Chisel types always match
        left <> right
    }
  }
}