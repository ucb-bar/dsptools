package freechips.rocketchip.amba.axi4stream

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util.{AsyncQueue, AsyncQueueParams, FromAsyncBundle, ToAsyncBundle}

class AXI4StreamAsyncCrossingSource(sync: Option[Int]) extends LazyModule()(Parameters.empty) {

  val node = AXI4StreamAsyncSourceNode(sync)

  lazy val module = new LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
        val psync = sync.getOrElse(edgeOut.bundle.async.sync)
        val params = edgeOut.slave.async.copy(sync = psync)
        out <> ToAsyncBundle(in, params)
    }
  }
}

object AXI4StreamAsyncCrossingSource {
  def apply(sync: Option[Int]): AXI4StreamAsyncSourceNode = {
    val axi4streamasource = LazyModule(new AXI4StreamAsyncCrossingSource(sync))
    axi4streamasource.node
  }
  def apply(sync: Int): AXI4StreamAsyncSourceNode = apply(Some(sync))
  def apply(): AXI4StreamAsyncSourceNode = apply(None)
}

class AXI4StreamAsyncCrossingSink(params: AsyncQueueParams = AsyncQueueParams()) extends LazyModule()(Parameters.empty) {
  val node = AXI4StreamAsyncSinkNode(params)

  lazy val module = new LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
        out <> FromAsyncBundle(in, params.sync)
    }
  }
}

object AXI4StreamAsyncCrossingSink {
  def apply(params: AsyncQueueParams = AsyncQueueParams()): AXI4StreamAsyncSinkNode = {
    val axi4streamsink = LazyModule(new AXI4StreamAsyncCrossingSink(params))
    axi4streamsink.node
  }
}
