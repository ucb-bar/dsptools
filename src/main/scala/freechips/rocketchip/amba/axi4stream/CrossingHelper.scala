package freechips.rocketchip.amba.axi4stream

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.CrossesToOnlyOneClockDomain

case class AXI4StreamInwardCrossingHelper(name: String, scope: LazyScope, node: AXI4StreamInwardNode) {
  def apply(xing: ClockCrossingType = NoCrossing)(implicit p: Parameters): AXI4StreamInwardNode = xing match {
    case a: AsynchronousCrossing =>
      node :*=*
        scope { AXI4StreamAsyncCrossingSink(a.asSinkParams) :*=* AXI4StreamAsyncNameNode(name) } :*=*
        AXI4StreamAsyncCrossingSource(a.sinkSync)
    case r@RationalCrossing(_) =>
      throw new NotImplementedError("AXI4Stream does not implement rational crossing yet")
    case SynchronousCrossing(buffer) =>
      node :*=* scope { AXI4StreamBuffer(buffer) :*=* AXI4StreamNameNode(name) }
  }
}

case class AXI4StreamOutwardCrossingHelper(name: String, scope: LazyScope, node: AXI4StreamOutwardNode) {
  def apply(xing: ClockCrossingType = NoCrossing)(implicit p: Parameters): AXI4StreamOutwardNode = xing match {
    case a: AsynchronousCrossing =>
      AXI4StreamAsyncCrossingSink(a.asSinkParams) :*=*
      scope {
        AXI4StreamAsyncNameNode(name) :*=*
        AXI4StreamAsyncCrossingSource(a.sourceSync)
      } :*=*
      node
    case r@RationalCrossing(_) =>
      throw new NotImplementedError("AXI4Stream does not implement rational crossing yet")
    case SynchronousCrossing(buffer) =>
      scope { AXI4StreamNameNode(name) :*=* AXI4StreamBuffer(buffer) } :*=* node
  }
}

trait HasAXI4StreamCrossing extends CrossesToOnlyOneClockDomain { this: LazyModule =>
  def crossAXI4StreamIn(n: AXI4StreamInwardNode): AXI4StreamInwardNode = {
    val axi4streamCrossing = this.crossIn(n)
    axi4streamCrossing(crossing)
  }

  def crossAXI4StreamOut(n: AXI4StreamOutwardNode): AXI4StreamOutwardNode = {
    val axi4streamCrossing = this.crossOut(n)
    axi4streamCrossing(crossing)
  }
}
