// See LICENSE for license details.

package ipxact

import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import firrtl.options.{Phase, TargetDirAnnotation}
import firrtl.stage.FirrtlCircuitAnnotation
import firrtl.{AnnotationSeq, CircuitState, HighForm, Transform}
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.util.{AddressMapAnnotation, ParamsAnnotation}
import org.scalatest.{FreeSpec, Matchers}

class IpxactGeneratorTransformTest extends FreeSpec with Matchers {

  class TestPhase(circuitState: CircuitState) extends Phase {

    val targets: Seq[Transform] = Seq(new IpxactGeneratorTransform)

    override def transform(annotations: AnnotationSeq): AnnotationSeq = {
      targets.foldLeft(circuitState)((a, f) => f.transform(a))
    }.annotations
  }

  "Ipxact Generator Transform should run these steps" in {

    val dut = LazyModule(new AXI4GCD())

    var annotations: AnnotationSeq = Seq(
      ChiselGeneratorAnnotation(() => dut.module),
      TargetDirAnnotation("test_run_dir/axi4gcd")
    )

    annotations = (new ChiselStage).run(annotations)

    val circuit = annotations.collectFirst {
      case FirrtlCircuitAnnotation(circuit) => circuit
    }.get

    annotations = new TestPhase(
      CircuitState(circuit, form = HighForm, annotations = annotations)
    ).transform(annotations)

    annotations.exists(_.isInstanceOf[AddressMapAnnotation]) should be(true)
    annotations.exists(_.isInstanceOf[ParamsAnnotation]) should be(true)
    annotations.exists(_.isInstanceOf[IpxactBundleName]) should be(true)
  }
}
