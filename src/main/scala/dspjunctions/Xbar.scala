package dspjunctions

import Chisel._
import cde._
import junctions._
import rocketchip.{ExtMemSize, PeripheryUtils, GlobalAddrMap}

case object InPorts extends Field[Int]
case object OutPorts extends Field[Int]
// Depth of *INTERNAL* crossbar queues
case object XBarQueueDepth extends Field[Int]
// Add queues at ports of crossbar. XBarQueueDepth does *NOT*
// set the depth of these queues
case object XBarUsePortQueues extends Field[Boolean]

class NastiXBar(topParams: Parameters) extends Module {
  implicit val p = topParams

  val io = new Bundle {
    val in = Vec(p(InPorts), new NastiIO).flip
    val out = Vec(p(OutPorts), new NastiIO)
  }

  val inPorts = p(InPorts)
  val addrMap = p(GlobalAddrMap)

  val bus = Module(new NastiRecursiveInterconnect(
    inPorts, addrMap, p(XBarQueueDepth)))
  bus.io.masters <> io.in.map(io => if (p(XBarUsePortQueues)) PeripheryUtils.addQueueAXI(io) else io)
  io.out <> bus.io.slaves.map(io => if (p(XBarUsePortQueues)) PeripheryUtils.addQueueAXI(io) else io)
}
