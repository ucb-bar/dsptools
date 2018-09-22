// See LICENSE for license details.

package dspblocks

import chisel3._
import freechips.rocketchip.amba.axi4stream.{AXI4StreamHierarchicalNode, AXI4StreamNode}
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy.LazyModuleImp

trait HierarchicalBlock[D, U, EO, EI, B <: Data] extends DspBlock[D, U, EO, EI, B] {
  type Block = DspBlock[D, U, EO, EI, B]
  def blocks: Seq[Block]
  def connections: Seq[(Block, Block)]
  def connect(lhs: Block, rhs: Block): Unit = {
    lhs.streamNode := rhs.streamNode
  }
  for (c <- connections) {
    connect(c._2, c._1)
  }
}

abstract class Chain[D, U, EO, EI, B <: Data](blockConstructors: Seq[Parameters => DspBlock[D, U, EO, EI, B]])
                                    (implicit p: Parameters) extends HierarchicalBlock[D, U, EO, EI, B] {
  override lazy val blocks: Seq[Block] = blockConstructors.map(_(p))
  override lazy val connections = blocks.sliding(2).map(x => (x(0), x(1))).toList
  override val streamNode: AXI4StreamNode = AXI4StreamHierarchicalNode(blocks.map(_.streamNode))
}

class APBChain(blockConstructors: Seq[Parameters => APBDspBlock])(implicit p: Parameters)
  extends Chain(blockConstructors) with APBDspBlockWithBus {
  for (b <- blocks) {
    b.mem.foreach { _ := bus.node }
  }

  lazy val module = new LazyModuleImp(this)
}

class AXI4Chain(blockConstructors: Seq[Parameters => AXI4DspBlock])(implicit p: Parameters)
  extends Chain(blockConstructors) with AXI4DspBlockWithBus {
  for (b <- blocks) {
    b.mem.foreach { _ := bus.node }
  }

  lazy val module = new LazyModuleImp(this)
}

class TLChain(blockConstructors: Seq[Parameters => TLDspBlock])(implicit p: Parameters)
  extends Chain(blockConstructors) with TLDspBlockWithBus {
  for (b <- blocks) {
    b.mem.foreach { _ := bus.node }
  }

  lazy val module = new LazyModuleImp(this)
}
