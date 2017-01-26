package craft

import chisel3._
import cde.{Parameters, Field}
import dsptools._
import dspblocks._
import dspjunctions._
import diplomacy.{LazyModule, LazyModuleImp}


class DspTop(p: Parameters) extends LazyModule {
  override lazy val module = Module(new DspTopModule(p, this, new DspTopBundle(p)))
}

class DspTopBundle(p: Parameters) extends BasicDspBlockIO()(p) {}

class DspTopModule[+L <: DspTop, +B <: DspTopBundle](val p: Parameters, l: L, b: => B)
  extends LazyModuleImp(l) with DspModule {
    val io = IO(b)
    io <> module.io
  }

case object BuildDSP extends Field[(Parameters) => DspBlock]

trait DspModule {
  implicit val p: Parameters
  val module = p(BuildDSP)(p)
}
