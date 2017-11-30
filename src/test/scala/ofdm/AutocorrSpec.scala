package ofdm

import breeze.math.Complex
import breeze.numerics.log10
import chisel3._
import chisel3.core.FixedPoint
import chisel3.iotesters.TesterOptionsManager
import co.theasi.plotly._
import dspblocks.{DspBlock, DspBlockBlindNodes, PeekPokePackers}
import dsptools.numbers._
import dsptools.numbers.implicits._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.coreplex.BaseCoreplexConfig
import freechips.rocketchip.diplomacy.{AddressSet, LazyModule, TransferSizes}
import ieee80211.IEEE80211
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.{Seq, mutable}

class AutocorrSpec extends FlatSpec with Matchers {
  implicit val p: Parameters = Parameters.root((new BaseCoreplexConfig).toInstance)

  val manager = new TesterOptionsManager {
    interpreterOptions = interpreterOptions.copy(setVerbose = false, writeVCD = true)
    testerOptions = testerOptions.copy(backendName = "firrtl")
  }

  def overlapDut(width:Int, depth:Int, delay: Int): () => OverlapSum[UInt] =
    () => new OverlapSum(UInt(width.W), depth, delay)

  def shrDut(width: Int, depth: Int): () => ShiftRegisterMem[UInt] =
    () => new ShiftRegisterMem(UInt(width.W), depth)

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

  def autocorrData[T <: Data : Ring](params: AutocorrParams[T], blindParams: DspBlock.AXI4BlindNodes, in: Seq[BigInt],
                                     overlap: Int=4, apart: Int=20): Seq[BigInt] = {
    val out = new mutable.ArrayBuffer[BigInt]()


    /*iotesters.Driver.execute(
      Array(/*"-tiv",*/ "-tbn", "firrtl", "-twffn", "out.vcd", "-fiwv"),
      () => Module(LazyModule(new AutocorrBlind(params, blindParams)).module)
    ) { c =>
      new AutocorrDataTester(c, in, out, overlap=overlap, apart=apart)
    }*/

    out
  }

  it should "work with DspComplex[SInt]" in {
    val params = AutocorrParams(
      DspComplex(SInt(32.W), SInt(32.W)),
      //DspComplex(FixedPoint(8.W, 4.BP), FixedPoint(8.W, 4.BP)),
      // genOut=Some(DspComplex(FixedPoint(16.W, 8.BP), FixedPoint(16.W, 8.BP))),
      maxApart = 32,
      maxOverlap = 8,
      address = AddressSet(0x0, 0xffffffffL),
      beatBytes = 8)
    val inWidthBytes = 8 //(params.genIn.getWidth + 7) / 8
    val outWidthBytes = 8 //params.genOut.map(x => (x.getWidth + 7)/8).getOrElse(inWidthBytes)

    println(s"In bytes = $inWidthBytes and out bytes = $outWidthBytes")

    val blindNodes = DspBlockBlindNodes(
      streamIn  = () => AXI4StreamMasterNode(Seq(AXI4StreamMasterPortParameters())),
      streamOut = () => AXI4StreamSlaveNode(Seq(AXI4StreamSlavePortParameters())),
      mem       = () => AXI4IdentityNode())

    val pattern = Seq(
      Complex(1,  0),
      Complex(0, 1),
      Complex(1, 0),
      Complex(0,  -1)
    )

    val values = (Seq.fill(100)(Complex(0,0)) ++ pattern ++ Seq.fill(16)(Complex(0, 0)) ++ pattern ++ Seq.fill(16)(Complex(0, 0)) ++ pattern ++ Seq.fill(16)(Complex(0, 0)) ++ pattern ++ Seq.fill(16)(Complex(0, 0)) ++ pattern ++ Seq.fill(16)(Complex(0, 0)) ++ pattern ++ Seq.fill(16)(Complex(0, 0)))
    .map(PeekPokePackers.pack(_, params.protoIn))

    println(s"Values are $values")

    val out = autocorrData(params, blindNodes, values)

    val unpackedOut = out.map(PeekPokePackers.unpack(_, params.protoOut.getOrElse(params.protoIn))).drop(100)

    println(s"Unpacked output is $unpackedOut")

    val xs = 0 to unpackedOut.length
    val ys = unpackedOut.map(_.abs)
    println(s"Absolute values are $ys")

    //val plot = Plot().withScatter(xs, ys)
    //draw(plot, "Autocorr Magnitude Signed")
  }

  it should "work with DspComplex[FixedPoint] and STF" in {
    val params = AutocorrParams(
      DspComplex(FixedPoint(32.W, 20.BP), FixedPoint(32.W, 20.BP)),
      //DspComplex(FixedPoint(8.W, 4.BP), FixedPoint(8.W, 4.BP)),
      // genOut=Some(DspComplex(FixedPoint(16.W, 8.BP), FixedPoint(16.W, 8.BP))),
      maxApart = 32,
      maxOverlap = 161,
      address = AddressSet(0x0, 0xffffffffL),
      beatBytes = 8)
    val inWidthBytes = 8 //(params.genIn.getWidth + 7) / 8
    val outWidthBytes = 8 //params.genOut.map(x => (x.getWidth + 7)/8).getOrElse(inWidthBytes)

    println(s"In bytes = $inWidthBytes and out bytes = $outWidthBytes")

    val blindNodes = DspBlockBlindNodes(
      streamIn  = () => AXI4StreamMasterNode(Seq(AXI4StreamMasterPortParameters())),
      streamOut = () => AXI4StreamSlaveNode(Seq(AXI4StreamSlavePortParameters())),
      mem       = () => AXI4IdentityNode())

    val values = Seq.fill(200)(Complex(0,0)) ++ IEEE80211.stf ++ Seq.fill(100)(Complex(0,0)) ++
      IEEE80211.stf ++ Seq.fill(300)(Complex(0,0)) ++ IEEE80211.stf ++ Seq.fill(200)(Complex(0,0))

    val out = autocorrData(
      params, blindNodes,
      values.map(PeekPokePackers.pack(_, params.protoIn)),
      overlap=160-16, apart=16
    )

    val unpackedOut = out.map(PeekPokePackers.unpack(_, params.protoOut.getOrElse(params.protoIn))).drop(200)

    println(s"Unpacked output is $unpackedOut")

    val xs = 0 to unpackedOut.length
    val ys = unpackedOut.map(x => 10*log10(x.abs))
    println(s"Absolute values are $ys")
 }
}
