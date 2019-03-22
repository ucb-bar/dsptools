// See LICENSE for license details.

package ipxact

import chisel3.core.annotate
import chisel3.experimental.{ChiselAnnotation, RawModule, RunFirrtlTransform}
import firrtl.annotations.Annotation

//TODO: (chick) Figure out what should trigger which AXI for device mapping to be usedin BusInterface
//TODO: (chick) Manage special handing of clock and reset in BusInterface generation
//TODO: (chick) Should parameters of submodules of Ipxact modules be found and included in generation
//TODO: (chick) Nothing has been implemented for addressSpaces XML tag

/**
  * This object's apply is used to mark a module as generating IP-XACT XML.
  *
  */
object Ipxact {
  // Used in MemoryMap Header
  val MemoryMapWidth: Int = 64

  def apply(rawModule: RawModule): Unit = {
    val ipxactBundleMemoizer: ChiselAnnotation with RunFirrtlTransform = new ChiselAnnotation with RunFirrtlTransform {
      //scalastyle:off public.methods.have.type
      def transformClass = classOf[IpxactBundleCaptureTransform]

      override def toFirrtl: Annotation = IpxactBundleMemoizerTrigger(rawModule.toNamed)
    }

    val ipxactGeneratorTriggerAnnotation: ChiselAnnotation with RunFirrtlTransform =
      new ChiselAnnotation with RunFirrtlTransform {

        //scalastyle:off public.methods.have.type
        def transformClass = classOf[IpxactGeneratorTransform]

        override def toFirrtl: Annotation = IpxactModuleAnnotation(rawModule.toNamed)
      }

    annotate(ipxactBundleMemoizer)              // This enqueues the HighForm BundleMemoizer transform
    annotate(ipxactGeneratorTriggerAnnotation)  // This enqueues the LowForm where actual XML generation happens
  }
}
