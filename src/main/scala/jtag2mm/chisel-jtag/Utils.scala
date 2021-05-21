// See ./LICENSE for license details.

package freechips.rocketchip.jtag2mm

import chisel3._
import chisel3.util._

// This code was taken from https://github.com/ucb-art/chisel-jtag/blob/master/src/main/scala/jtag/Utils.scala and adjusted to our design needs

/** Bundle representing a tristate pin.
  */
class Tristate extends Bundle {
  val data = Bool()
  val driven = Bool() // active high, pin is hi-Z when driven is low
}

class NegativeEdgeLatch[T <: Data](dataType: T) extends Module {
  class IoClass extends Bundle {
    val next = Input(dataType)
    val enable = Input(Bool())
    val output = Output(dataType)
  }
  val io = IO(new IoClass)

  val reg = Reg(dataType)
  when(io.enable) {
    reg := io.next
  }
  io.output := reg
}

/** Generates a register that updates on the falling edge of the input clock signal.
  */
object NegativeEdgeLatch {
  def apply[T <: Data](modClock: Clock, next: T, enable: Bool = true.B): T = {
    // TODO better init passing once in-module multiclock support improves

    val latch_module = withClock((!(modClock.asUInt)).asClock) {
      Module(new NegativeEdgeLatch(chiselTypeOf(next)))
    }
    latch_module.io.next := next
    latch_module.io.enable := enable
    latch_module.io.output
  }
}

/** A module that counts transitions on the input clock line, used as a basic sanity check and
  * debug indicator clock-crossing designs.
  */
class ClockedCounter(counts: BigInt, init: Option[BigInt]) extends Module {
  require(counts > 0, "really?")

  val width = log2Ceil(counts)
  class CountIO extends Bundle {
    val count = Output(UInt(width.W))
  }
  val io = IO(new CountIO)

  val count = init match {
    case Some(init) => RegInit(UInt(width.W), init.U)
    case None       => Reg(UInt(width.W))
  }

  when(count === (counts - 1).asUInt) {
    count := 0.U
  }.otherwise {
    count := count + 1.U
  }

  io.count := count
}

/** Count transitions on the input bit by specifying it as a clock to a counter.
  */
object ClockedCounter {
  def apply(data: Bool, counts: BigInt, init: BigInt): UInt = {
    val counter = withClock(data.asClock) {
      Module(new ClockedCounter(counts, Some(init)))
    }
    counter.io.count
  }
  def apply(data: Bool, counts: BigInt): UInt = {
    val counter = withClock(data.asClock) {
      Module(new ClockedCounter(counts, None))
    }
    counter.io.count
  }
}
