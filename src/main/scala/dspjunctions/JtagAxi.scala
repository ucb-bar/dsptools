// See LICENSE for license details.

package dspjunctions

import cde._
import chisel3._
import chisel3.experimental.withClockAndReset
import chisel3.util._
import jtag._
import junctions._
import uncore.converters._

case object DspChainIncludeJtag extends Field[Bool]

class DspJtagIO extends JtagIO {
  // val TRST = Input(Bool())
}

trait HasDspJtagParameters {
  implicit val p: Parameters
  val includeJtag = try {
    if (p(DspChainIncludeJtag)) {
      true
    } else {
      false
    }
  } catch {
    case _: ParameterUndefinedException => false
  }
}

trait HasDspJtagIO extends HasDspJtagParameters {
  val p: Parameters
  val jtag = if (includeJtag) {
      Some(new DspJtagIO)
  } else {
    None
  }
}

trait HasDspJtagModule extends HasDspJtagParameters {
  val io: HasDspJtagIO

  val jtagMaster = if (includeJtag) {
    Some(Module(new JtagAxiMaster()))
  } else {
    None
  }

  def jtagCtrlAxiMasters: Option[NastiIO] = jtagMaster.map(m => {
    m.io.ctrlAxi
  })
  def jtagDataAxiMasters: Option[NastiIO] = jtagMaster.map(m => {
    m.io.ctrlAxi
  })

  def jtagConnect: Unit = (io.jtag, jtagMaster) match {
    case (Some(jtag), Some(master)) =>
      jtag <> master.io.jtag
    case (None, None) =>
    case _ => throw dsptools.DspException("One of jtagMaster and io.jtag existed, but the other did not!")
  }
}

class JtagAxiMaster()(implicit p: Parameters) extends NastiModule()(p) {
  val io = IO(new Bundle {
    val jtag    = new DspJtagIO
    val ctrlAxi = new NastiIO
    val dataAxi = new NastiIO
  })
  val irLength = 4
  val idcode = (BigInt(14), JtagIdcode(1, 1, 0x42))

  val jtagClock = io.jtag.TCK.asClock
  val jtagReset = Wire(Bool()) // reset=io.jtag.TRST

  val (innerCtrlAxi, innerDataAxi) = withClockAndReset(clock = jtagClock, reset=jtagReset) {
    val innerCtrlAxi = Wire(new NastiIO)
    val innerDataAxi = Wire(new NastiIO)
    val ctrl_aw_chain = Module(new DecoupledSourceChain(new NastiWriteAddressChannel))
    val ctrl_w_chain  = Module(new DecoupledSourceChain(new NastiWriteDataChannel))
    val ctrl_b_chain  = Module(new DecoupledSinkChain(new NastiWriteResponseChannel))
    val ctrl_ar_chain = Module(new DecoupledSourceChain(new NastiReadAddressChannel))
    val ctrl_r_chain  = Module(new DecoupledSinkChain(new NastiReadDataChannel))

    val data_aw_chain = Module(new DecoupledSourceChain(new NastiWriteAddressChannel))
    val data_w_chain  = Module(new DecoupledSourceChain(new NastiWriteDataChannel))
    val data_b_chain  = Module(new DecoupledSinkChain(new NastiWriteResponseChannel))
    val data_ar_chain = Module(new DecoupledSourceChain(new NastiReadAddressChannel))
    val data_r_chain  = Module(new DecoupledSinkChain(new NastiReadDataChannel))

    val tapIO = JtagTapGenerator(irLength, Map(
      0 -> ctrl_aw_chain,
      1 -> ctrl_w_chain,
      2 -> ctrl_b_chain,
      3 -> ctrl_ar_chain,
      4 -> ctrl_r_chain,
      5 -> data_aw_chain,
      6 -> data_w_chain,
      7 -> data_b_chain,
      8 -> data_ar_chain,
      9 -> data_r_chain
    ), idcode = Some(idcode))

    io.jtag <> tapIO.jtag
    tapIO.control.fsmAsyncReset := false.B
    jtagReset := tapIO.output.reset
    // tapIO.output?

    innerCtrlAxi.aw <> ctrl_aw_chain.io.interface
    innerCtrlAxi.w  <> ctrl_w_chain.io.interface
    innerCtrlAxi.b  <> ctrl_b_chain.io.interface
    innerCtrlAxi.ar <> ctrl_ar_chain.io.interface
    innerCtrlAxi.r  <> ctrl_r_chain.io.interface

    innerDataAxi.aw <> data_aw_chain.io.interface
    innerDataAxi.w  <> data_w_chain.io.interface
    innerDataAxi.b  <> data_b_chain.io.interface
    innerDataAxi.ar <> data_ar_chain.io.interface
    innerDataAxi.r  <> data_r_chain.io.interface
    (innerCtrlAxi, innerDataAxi)
  }
  // io.jtag <> tap.io.jtag

  io.ctrlAxi <> AsyncNastiFrom(from_clock=jtagClock, from_reset=jtagReset, from_source=innerCtrlAxi)
  io.dataAxi <> AsyncNastiFrom(from_clock=jtagClock, from_reset=jtagReset, from_source=innerDataAxi)
}
