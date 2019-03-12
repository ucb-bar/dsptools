// See LICENSE for license details.

package ipxact

import chisel3.core.annotate
import chisel3.experimental.{ChiselAnnotation, RawModule, RunFirrtlTransform}
import firrtl.RenameMap
import firrtl.annotations.{Annotation, SingleTargetAnnotation, Target}

case class IpxactModuleAnnotation(target: Target) extends SingleTargetAnnotation[Target] {

  override def duplicate(n: Target): Annotation = {this.copy(n)}

  override def update(renames: RenameMap): Seq[Annotation] = {
    Seq(duplicate(target))
  }

}

object Ipxact {
  def apply(rawModule: RawModule): Unit = {
    val chiselAnnotation = new ChiselAnnotation with RunFirrtlTransform {
      def transformClass = classOf[IpxactGeneratorTransform]

      override def toFirrtl: Annotation = IpxactModuleAnnotation(rawModule.toNamed)
    }
    annotate(chiselAnnotation)
  }
}
