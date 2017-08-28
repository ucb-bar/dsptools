package autocorr

import chisel3._
import chisel3.iotesters.TesterOptionsManager
import dspblocks.{DspBlock, DspBlockBlindNodes}
import dsptools.numbers._
import dsptools.numbers.implicits._
import freechips.rocketchip.amba.apb.{APBMasterParameters, APBMasterPortParameters}
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.coreplex.BaseCoreplexConfig
import freechips.rocketchip.diplomacy.{AddressSet, LazyModule, TransferSizes}
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.mutable

class AutocorrSpec extends FlatSpec with Matchers {
  implicit val p: Parameters = Parameters.root((new BaseCoreplexConfig).toInstance)

  val manager = new TesterOptionsManager {
    interpreterOptions = interpreterOptions.copy(setVerbose = false, writeVCD = true)
  }

  def overlapDut(width:Int, depth:Int, delay: Int): () => OverlapSum[UInt] =
    () => new OverlapSum(UInt(width.W), depth, delay)

  def shrDut(width: Int, depth: Int): () => AutocorrShiftRegister[UInt] =
    () => new AutocorrShiftRegister(UInt(width.W), depth)

  behavior of "OverlapSum"

  it should "work with depth 1" in {
    iotesters.Driver.execute(overlapDut(8, 4, 0), optionsManager = manager) { c =>
      new OverlapSumTester(c, 1) } should be (true)
  }

  it should "work with full depth" in {
    iotesters.Driver(overlapDut(8, 4, 0)) { c =>
      new OverlapSumTester(c, 4) } should be (true)
  }

  it should "work with pipeline delays" in {
    iotesters.Driver(overlapDut(8, 4, 5)) { c =>
      new OverlapSumTester(c, 4) } should be (true)
  }


  behavior of "ShiftRegister"

  it should "work with depth 1" in {
    iotesters.Driver.execute(shrDut(8, 4), optionsManager = manager) { c =>
      new ShiftRegisterTester(c, 1)
    } should be (true)
  }

  it should "work with full depth" in {
    iotesters.Driver.execute(shrDut(8, 8), optionsManager = manager) { c =>
      new ShiftRegisterTester(c, 7)
    } should be (true)
  }

  behavior of "Autocorr"

  def autocorrData[T <: Data : Ring](params: AutocorrParams[T], blindParams: DspBlock.AXI4BlindNodes, in: Seq[BigInt]): Seq[BigInt] = {
    val out = new mutable.ArrayBuffer[BigInt]()


    iotesters.Driver.execute(
      () => LazyModule(new AutocorrBlind(params, blindParams)).module, optionsManager = manager) { c =>
      new AutocorrDataTester(c, in, out)
    }

    out.toSeq
  }

  it should "work with DspComplex[SInt]" in {
    val params = AutocorrParams(
      DspComplex(SInt(8.W), SInt(8.W)),
      maxApart = 16,
      maxOverlap = 4,
      address = AddressSet(0x0, 0xffffffffL),
      beatBytes = 8)
    val blindNodes = DspBlockBlindNodes(
      streamIn  = () => AXI4StreamBlindInputNode(Seq(AXI4StreamMasterPortParameters(Seq(AXI4StreamMasterParameters(
        "autocorr"
      ))))),
      streamOut = () => AXI4StreamBlindOutputNode(Seq(AXI4StreamSlavePortParameters(Seq(AXI4StreamSlaveParameters(
        bundleParams = AXI4StreamBundleParameters(1)
      ))))),
      mem       = () => AXI4BlindInputNode(Seq(AXI4MasterPortParameters(Seq(
        AXI4MasterParameters(
          "autocorr"))))

      ))

    val out = autocorrData(params, blindNodes, (0 until 2000).map(BigInt(_)))

    println(out)
  }
}
