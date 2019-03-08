// See LICENSE for license details.

package ipxact

import chisel3.core.annotate
import chisel3.internal.NamedComponent
import chisel3.experimental.{ChiselAnnotation, RawModule, RunFirrtlTransform}
import firrtl.{RenameMap, Transform}
import firrtl.annotations.{Annotation, Named}
import firrtl.passes.wiring.WiringTransform

case class IpxactModuleAnnotation(target: Named) extends Annotation {

  def update(renames: RenameMap): Seq[Annotation] = Seq.empty

}

object Ipxact {
  def apply(rawModule: RawModule): Unit = {
    val chiselAnnotation = new ChiselAnnotation with RunFirrtlTransform {
      def transformClass = classOf[WiringTransform]

      override def toFirrtl: Annotation = IpxactModuleAnnotation(rawModule.toNamed)
    }
    annotate(chiselAnnotation)
  }
}
