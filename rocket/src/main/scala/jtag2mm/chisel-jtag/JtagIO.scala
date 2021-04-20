// See ./LICENSE for license details.

package freechips.rocketchip.jtag2mm

import chisel3._

// This code was taken from https://github.com/ucb-art/chisel-jtag/blob/master/src/main/scala/jtag/jtagTap.scala and adjusted to our design needs

/** JTAG signals, viewed from the device side.
  */
class JtagIO extends Bundle {
  // TRST (4.6) is optional and not currently implemented.
  val TCK = Input(Bool())
  val TMS = Input(Bool())
  val TDI = Input(Bool())
  val TDO = Output(new Tristate())
}

/** JTAG block output signals.
  */
class JtagOutput(irLength: Int) extends Bundle {
  val state = Output(JtagState.State.chiselType()) // state, transitions on TCK rising edge
  val instruction = Output(UInt(irLength.W)) // current active instruction
  val reset = Output(Bool()) // synchronous reset asserted in Test-Logic-Reset state, should NOT hold the FSM in reset

  override def cloneType = new JtagOutput(irLength).asInstanceOf[this.type]
}

class JtagControl extends Bundle {
  val fsmAsyncReset = Input(Bool()) // TODO: asynchronous reset for FSM, used for TAP_POR*
}

/** Aggregate JTAG block IO.
  */
class JtagBlockIO(val irLength: Int) extends Bundle {
  val jtag = new JtagIO
  val control = new JtagControl
  val output = new JtagOutput(irLength)
}

/** Internal controller block IO with data shift outputs.
  */
class JtagControllerIO(irLength: Int) extends JtagBlockIO(irLength) {
  val dataChainOut = Output(new ShifterIO)
  val dataChainIn = Input(new ShifterIO)
}
