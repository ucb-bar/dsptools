// See LICENSE for license details.

package ipxact

import chisel3.core.annotate
import chisel3.experimental.{ChiselAnnotation, RawModule, RunFirrtlTransform}
import firrtl.RenameMap
import firrtl.annotations.{Annotation, ModuleTarget, SingleTargetAnnotation, Target}

/**
  * This triggers a pre-lowering transform to run and to capture the ipxact bundle names
  * @param target the module begin memoized
  */
case class IpxactBundleMemoizerTrigger(target: ModuleTarget) extends SingleTargetAnnotation[Target] {
  override def duplicate(n: Target): Annotation = {this.copy(n.asInstanceOf[ModuleTarget])}

  override def update(renames: RenameMap): Seq[Annotation] = {
    Seq(duplicate(target))
  }
}

/**
  * This annotations identifies a module to be processed by the IpxactGeneratorTransform
  * @param target the module
  */
//TODO: (chick) consider to allow a name to override the xml header name
case class IpxactModuleAnnotation(target: ModuleTarget) extends SingleTargetAnnotation[Target] {

  override def duplicate(n: Target): Annotation = {this.copy(n.asInstanceOf[ModuleTarget])}

  override def update(renames: RenameMap): Seq[Annotation] = {
    Seq(duplicate(target))
  }
}

object Ipxact {
  def apply(rawModule: RawModule): Unit = {
    val ipxactBundleMemoizer: ChiselAnnotation with RunFirrtlTransform = new ChiselAnnotation with RunFirrtlTransform {
      def transformClass = classOf[IpxactBundleCaptureTransform]

      override def toFirrtl: Annotation = IpxactBundleMemoizerTrigger(rawModule.toNamed)
    }

    val ipxactGeneratorTriggerAnnotation: ChiselAnnotation with RunFirrtlTransform =
      new ChiselAnnotation with RunFirrtlTransform {

      def transformClass = classOf[IpxactGeneratorTransform]

      override def toFirrtl: Annotation = IpxactModuleAnnotation(rawModule.toNamed)
    }
    annotate(ipxactBundleMemoizer)
    annotate(ipxactGeneratorTriggerAnnotation)
  }
}
