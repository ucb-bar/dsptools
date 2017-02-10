package craft

import chisel3._
import cde._
import dsptools._
import dspblocks._
import dspjunctions._
import diplomacy.{LazyModule, LazyModuleImp}

class DspTop(p: Parameters) extends Module {
  val module = try {
    // DspBlock
    val lazyModule = LazyModule(new DspBlockTop(p))
    val module     = lazyModule.module
    module
  } catch {
    case _: ParameterUndefinedException =>
    // DspChain
    val lazyModule = LazyModule(new DspChainTop(p))
    val module     = lazyModule.module
    module
  }
  val io = IO(Flipped(module.io.cloneType))
}

class DspBlockTop(p: Parameters) extends LazyModule {
  override lazy val module = Module(new DspBlockTopModule(p, this))
}

class DspBlockTopModule[+L <: DspBlockTop](val p: Parameters, l: L) extends LazyModuleImp(l) with HasDspBlock {
    val io = IO(module.io.cloneType)
    io <> module.io
  }

case object BuildDSPBlock extends Field[Parameters => LazyDspBlock]

trait HasDspBlock {
  implicit val p: Parameters
  val lazyModule = LazyModule(p(BuildDSPBlock)(p))
  val module = Module(lazyModule.module)
}

class DspChainTop(p: Parameters) extends LazyModule {
  override lazy val module = Module(new DspChainTopModule(p, this))
}

class DspChainTopModule[+L <: DspChainTop](val p: Parameters, l: L) extends LazyModuleImp(l) with HasDspChain {
    val io = IO(Flipped(module.io.cloneType))
    io <> module.io
  }

case object BuildDSPChain extends Field[Parameters => DspChain]

trait HasDspChain {
  implicit val p: Parameters
  val module = Module(p(BuildDSPChain)(p))//.module
}
