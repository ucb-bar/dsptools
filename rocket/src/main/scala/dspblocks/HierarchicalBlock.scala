// See LICENSE for license details.

package dspblocks

import chisel3._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy.{LazyModuleImp, NodeHandle}

trait HierarchicalBlock[D, U, EO, EI, B <: Data] extends DspBlock[D, U, EO, EI, B] {
  type Block = DspBlock[D, U, EO, EI, B]
  def blocks: Seq[Block]
  def connections: Seq[(Block, Block)]
  def connect(lhs: Block, rhs: Block): AXI4StreamNodeHandle = {
    lhs.streamNode := rhs.streamNode
  }
  for ((lhs, rhs) <- connections) {
    connect(lhs, rhs)
  }
}

abstract class Chain[D, U, EO, EI, B <: Data](blockConstructors: Seq[Parameters => DspBlock[D, U, EO, EI, B]])
                                    (implicit p: Parameters) extends HierarchicalBlock[D, U, EO, EI, B] {
  override lazy val blocks: Seq[Block] = blockConstructors.map(_(p))
  override lazy val connections = for (i <- 1 until blocks.length) yield (blocks(i), blocks(i-1))
  override lazy val streamNode = NodeHandle(blocks.head.streamNode, blocks.last.streamNode)
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
