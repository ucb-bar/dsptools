// See LICENSE for license details.

package ipxact

import firrtl.{CircuitForm, CircuitState, HighForm, Transform}
import firrtl.annotations.ComponentName
import freechips.rocketchip.util.ParamsAnnotation

class IpxactBundleCaptureTransform extends Transform {
  override def inputForm: CircuitForm = HighForm

  override def outputForm: CircuitForm = HighForm

  override def execute(state: CircuitState): CircuitState = {
    val bundleAnnotations = state.annotations.flatMap {
      case a: ParamsAnnotation if a.paramsClassName == "freechips.rocketchip.amba.axi4.AXI4BundleParameters" =>
        a.target match {
          case ComponentName(componentName, moduleName) =>
            Some(IpxactBundleName(moduleName, componentName))
          case _ =>
            None
        }
      case _ =>
        Seq.empty
    }
    state.copy(annotations = state.annotations ++ bundleAnnotations)
  }
}
