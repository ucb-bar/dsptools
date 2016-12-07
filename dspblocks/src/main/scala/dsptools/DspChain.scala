// See LICENSE for license details

package dsptools

import cde._
import chisel3._
import debuggers._
import _root_.junctions._
import sam._
import testchipip._
import uncore.converters._

case class DspChainParameters (
  blocks: Seq[() => DspBlock],
  baseAddr: Int,
  samConfig: SAMConfig,
  logicAnalyzerSamples: Int,
  patternGeneratorSamples: Int,
  patternGeneratorTrigger: Boolean = true
)

case object DspChainKey extends Field[DspChainParameters]

trait HasDspChainParameters {
  implicit val p: Parameters
  val blocks = p(DspChainKey).blocks
  val baseAddr = p(DspChainKey).baseAddr
  val samConfig = p(DspChainKey).samConfig
  val logicAnalyzerSamples = p(DspChainKey).logicAnalyzerSamples
  val patternGeneratorSamples = p(DspChainKey).patternGeneratorSamples
  val patternGeneratorTrigger = p(DspChainKey).patternGeneratorTrigger
}

class DspChainIO()(implicit val p: Parameters) extends Bundle with HasDspBlockParameters {
  val axi = new NastiIO().flip
}

class DspChain(
  b: => Option[DspChainIO] = None,
  override_clock: Option[Clock]=None,
  override_reset: Option[Bool]=None)(implicit val p: Parameters)
  extends Module(override_clock, override_reset)
  with HasDspChainParameters {
  val io = IO(b.getOrElse(new DspChainIO))

  val modules = blocks.map(_())
  val mod_ios = modules.map(_.io)

  val maxDataWidth = mod_ios.map(i =>
      math.max(i.in.getWidth, i.out.getWidth)
  ).reduce(math.max(_, _))

  val lastDataWidth = mod_ios.last.out.getWidth

  val logicAnalyzer = Module( new LogicAnalyzer(maxDataWidth, 1, logicAnalyzerSamples) )

  val patternGenerator = Module( new PatternGenerator(maxDataWidth, 1, patternGeneratorSamples) )

  val sam = Module( new SAM(lastDataWidth, samConfig) )

  val scrbuilder = new SCRBuilder(name)

  scrbuilder.addControl("logicAnalyzerSelect", 0.U)
  scrbuilder.addControl("patternGeneratorSelect", 0.U)

  val scr = scrbuilder.generate(baseAddr)
  val tl2axi = Module(new TileLinkIONastiIOConverter())
  tl2axi.io.tl <> scr.io.tl
  
  val axis = tl2axi.io.nasti +: mod_ios.map(_.axi)

  val logicAnalyzerSelect = scr.control("logicAnalyzerSelect")
  val patternGeneratorSelect = scr.control("patternGeneratorSelect")

  mod_ios.zipWithIndex.sliding(2).foreach({case (l, lidx)::(r, ridx) :: Nil =>
    when (logicAnalyzerSelect === lidx.U) {
      logicAnalyzer.io.signal  := l.in
      logicAnalyzer.io.trigger := true.B
    }
    when (patternGeneratorSelect === ridx.U) {
      r.in.bits := patternGenerator.io.signal.bits
      r.in.valid := patternGenerator.io.signal.valid
      patternGenerator.io.signal.ready := true.B
    } .otherwise {
      r.in <> l.out
    }
  })

}
