// See LICENSE for license details.

package ipxact

import firrtl.RenameMap
import firrtl.annotations._

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

/**
  * This class memoizes a Bundle that will be referenced by Ipxact
  * Without this the bundles original name will be lost during bundle expansion in firrtl compiler
  * @param moduleName the module name
  * @param bundleName the bundle name we want to save
  */
case class IpxactBundleName(moduleName: ModuleTarget, bundleName: String) extends Annotation {
  override def update(renames: RenameMap): Seq[Annotation] = Seq(this.copy())
}

/**
  * This annotation carries the generated IP-XACT xml.
  * @param xmlDocument the document
  */
//TODO: (chick) Is there any merit in passing this down the compiler stack
case class GeneratedIpxactFileNameAnnotation(fileName: String) extends NoTargetAnnotation
