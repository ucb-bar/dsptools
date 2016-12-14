// See LICENSE for license details.

package dsptools

import chisel3._
import chisel3.internal.InstanceId
import firrtl.{CircuitForm, CircuitState, LowForm, Transform}
import firrtl.annotations.{Annotation, Named}

object ResetInverterAnnotation {
  def apply(target: Named, value: String): Annotation = Annotation(target, classOf[ResetInverterTransform], "invert")
}

class ResetInverterTransform extends Transform {
  override def inputForm: CircuitForm = LowForm
  override def outputForm: CircuitForm = LowForm

  override def execute(state: CircuitState): CircuitState = {
    getMyAnnotations(state) match {
      case Nil => state
      case invertedAnnotatons =>
        state
    }
  }
}

trait ResetInverter {
  self: Module =>
  def invert(component: InstanceId): Unit = {
    annotate(ChiselAnnotation(component, classOf[ResetInverterTransform], "invert"))
  }
}
