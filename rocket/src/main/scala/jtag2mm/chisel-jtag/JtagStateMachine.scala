// See ./LICENSE for license details.

package freechips.rocketchip.jtag2mm

import chisel3._
import chisel3.util._

// This code was taken from https://github.com/ucb-art/chisel-jtag/blob/master/src/main/scala/jtag/jtagStateMachine.scala and adjusted to our design needs

object JtagState {
  sealed abstract class State(val id: Int) {
    def U: UInt = id.U(State.width.W)
  }

  object State {
    import scala.language.implicitConversions

    implicit def toInt(x:    State) = x.id
    implicit def toBigInt(x: State): BigInt = x.id

    // TODO: this could be automatically generated with macros and stuff
    val all: Set[State] = Set(
      TestLogicReset,
      RunTestIdle,
      SelectDRScan,
      CaptureDR,
      ShiftDR,
      Exit1DR,
      PauseDR,
      Exit2DR,
      UpdateDR,
      SelectIRScan,
      CaptureIR,
      ShiftIR,
      Exit1IR,
      PauseIR,
      Exit2IR,
      UpdateIR
    )
    val width = log2Ceil(all.size)
    def chiselType() = UInt(width.W)
  }

  // States as described in 6.1.1.2, numeric assignments from example in Table 6-3
  case object TestLogicReset
      extends State(15) // no effect on system logic, entered when TMS high for 5 TCK rising edges
  case object RunTestIdle extends State(12) // runs active instruction (which can be idle)
  case object SelectDRScan extends State(7)
  case object CaptureDR extends State(6) // parallel-load DR shifter when exiting this state (if required)
  case object ShiftDR
      extends State(
        2
      ) // shifts DR shifter from TDI towards TDO, last shift occurs on rising edge transition out of this state
  case object Exit1DR extends State(1)
  case object PauseDR extends State(3) // pause DR shifting
  case object Exit2DR extends State(0)
  case object UpdateDR
      extends State(5) // parallel-load output from DR shifter on TCK falling edge while in this state (not a rule?)
  case object SelectIRScan extends State(4)
  case object CaptureIR
      extends State(
        14
      ) // parallel-load IR shifter with fixed logic values and design-specific when exiting this state (if required)
  case object ShiftIR
      extends State(
        10
      ) // shifts IR shifter from TDI towards TDO, last shift occurs on rising edge transition out of this state
  case object Exit1IR extends State(9)
  case object PauseIR extends State(11) // pause IR shifting
  case object Exit2IR extends State(8)
  case object UpdateIR
      extends State(
        13
      ) // latch IR shifter into IR (changes to IR may only occur while in this state, latch on TCK falling edge)
}

/** The JTAG state machine, implements spec 6.1.1.1a (Figure 6.1)
  *
  * Usage notes:
  * - 6.1.1.1b state transitions occur on TCK rising edge
  * - 6.1.1.1c actions can occur on the following TCK falling or rising edge
  */
class JtagStateMachine extends Module {
  class StateMachineIO extends Bundle {
    val tms = Input(Bool())
    val currState = Output(JtagState.State.chiselType())

    val asyncReset = Input(Bool()) // TODO: IMPLEMENT ME, make it actually async
  }
  val io = IO(new StateMachineIO)

  // TMS is captured as a single signal, rather than fed directly into the next state logic.
  // This increases the state computation delay at the beginning of a cycle (as opposed to near the
  // end), but theoretically allows a cleaner capture.
  val tms = RegNext(io.tms) // 4.3.1a captured on TCK rising edge, 6.1.2.1b assumed changes on TCK falling edge

  withReset(io.asyncReset) {
    val nextState = Wire(JtagState.State.chiselType())
    nextState := DontCare //TODO: figure out what isn't getting connected
    val lastState = RegNext(nextState, JtagState.TestLogicReset.U)

    switch(lastState) {
      is(JtagState.TestLogicReset.U) {
        nextState := Mux(tms, JtagState.TestLogicReset.U, JtagState.RunTestIdle.U)
      }
      is(JtagState.RunTestIdle.U) {
        nextState := Mux(tms, JtagState.SelectDRScan.U, JtagState.RunTestIdle.U)
      }
      is(JtagState.SelectDRScan.U) {
        nextState := Mux(tms, JtagState.SelectIRScan.U, JtagState.CaptureDR.U)
      }
      is(JtagState.CaptureDR.U) {
        nextState := Mux(tms, JtagState.Exit1DR.U, JtagState.ShiftDR.U)
      }
      is(JtagState.ShiftDR.U) {
        nextState := Mux(tms, JtagState.Exit1DR.U, JtagState.ShiftDR.U)
      }
      is(JtagState.Exit1DR.U) {
        nextState := Mux(tms, JtagState.UpdateDR.U, JtagState.PauseDR.U)
      }
      is(JtagState.PauseDR.U) {
        nextState := Mux(tms, JtagState.Exit2DR.U, JtagState.PauseDR.U)
      }
      is(JtagState.Exit2DR.U) {
        nextState := Mux(tms, JtagState.UpdateDR.U, JtagState.ShiftDR.U)
      }
      is(JtagState.UpdateDR.U) {
        nextState := Mux(tms, JtagState.SelectDRScan.U, JtagState.RunTestIdle.U)
      }
      is(JtagState.SelectIRScan.U) {
        nextState := Mux(tms, JtagState.TestLogicReset.U, JtagState.CaptureIR.U)
      }
      is(JtagState.CaptureIR.U) {
        nextState := Mux(tms, JtagState.Exit1IR.U, JtagState.ShiftIR.U)
      }
      is(JtagState.ShiftIR.U) {
        nextState := Mux(tms, JtagState.Exit1IR.U, JtagState.ShiftIR.U)
      }
      is(JtagState.Exit1IR.U) {
        nextState := Mux(tms, JtagState.UpdateIR.U, JtagState.PauseIR.U)
      }
      is(JtagState.PauseIR.U) {
        nextState := Mux(tms, JtagState.Exit2IR.U, JtagState.PauseIR.U)
      }
      is(JtagState.Exit2IR.U) {
        nextState := Mux(tms, JtagState.UpdateIR.U, JtagState.ShiftIR.U)
      }
      is(JtagState.UpdateIR.U) {
        nextState := Mux(tms, JtagState.SelectDRScan.U, JtagState.RunTestIdle.U)
      }
    }

    io.currState := nextState
  }
}
