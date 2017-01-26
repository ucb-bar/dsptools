package sam

import craft._
import diplomacy.LazyModule
import rocketchip._
import testchipip._
import chisel3._
import cde.Parameters

class TestHarness(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    //val success = Output(Bool())
  })

  def buildTop(p: Parameters): DspTop = LazyModule(new DspTop(p))

  val dut = buildTop(p).module
}
